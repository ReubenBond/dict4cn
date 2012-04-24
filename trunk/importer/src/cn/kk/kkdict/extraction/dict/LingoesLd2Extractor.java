package cn.kk.kkdict.extraction.dict;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import cn.kk.kkdict.beans.FormattedTreeMap;
import cn.kk.kkdict.types.Category;
import cn.kk.kkdict.types.Language;
import cn.kk.kkdict.types.TranslationSource;
import cn.kk.kkdict.utils.ArrayHelper;
import cn.kk.kkdict.utils.ChineseHelper;
import cn.kk.kkdict.utils.Helper;

public class LingoesLd2Extractor {
    private static final ArrayHelper.SensitiveStringDecoder[] AVAIL_ENCODINGS = {
            new ArrayHelper.SensitiveStringDecoder(Charset.forName("UTF-8")),
            new ArrayHelper.SensitiveStringDecoder(Charset.forName("UTF-16LE")),
            new ArrayHelper.SensitiveStringDecoder(Charset.forName("UTF-16BE")),
            new ArrayHelper.SensitiveStringDecoder(Charset.forName("EUC-JP")) };

    private static final byte[] TRANSFER_BYTES = new byte[Helper.BUFFER_SIZE];
    public static final String IN_DIR = Helper.DIR_IN_DICTS + "\\lingoes";
    public static final String OUT_DIR = Helper.DIR_OUT_DICTS + "\\lingoes";

    public static void main(String[] args) throws IOException {
        File directory = new File(IN_DIR);
        if (directory.isDirectory()) {
            System.out.print("搜索灵格斯LD2文件'" + IN_DIR + "' ... ");
            new File(OUT_DIR).mkdirs();

            File[] files = directory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".ld2");
                }
            });
            System.out.println(files.length);

            long total = 0;
            for (File f : files) {
                System.out.print("分析'" + f + " ... ");
                int counter = extractLd2ToFile(f);
                if (counter > 0) {
                    System.out.println(counter);
                    total += counter;
                }
            }

            System.out.println("\n=====================================");
            System.out.println("成功读取了" + files.length + "个Lingoes LD2文件");
            System.out.println("总共单词：" + total);
            System.out.println("=====================================");
        }
    }

    private static int extractLd2ToFile(File ld2File) throws IOException {
        Helper.precheck(ld2File.getAbsolutePath(), OUT_DIR);
        int counter = 0;

        // read lingoes ld2 into byte array
        FileChannel fChannel = new RandomAccessFile(ld2File, "r").getChannel();
        ByteBuffer dataRawBytes = ByteBuffer.allocate((int) fChannel.size());
        fChannel.read(dataRawBytes);
        fChannel.close();
        dataRawBytes.order(ByteOrder.LITTLE_ENDIAN);
        dataRawBytes.rewind();

        int offsetData = dataRawBytes.getInt(0x5C) + 0x60;
        if (dataRawBytes.limit() > offsetData) {
            int type = dataRawBytes.getInt(offsetData);
            int offsetWithInfo = dataRawBytes.getInt(offsetData + 4) + offsetData + 12;
            if (type == 3) {
                counter = readDictionary(ld2File, dataRawBytes, offsetData);
            } else if (dataRawBytes.limit() > offsetWithInfo + 0x1C) {
                counter = readDictionary(ld2File, dataRawBytes, offsetWithInfo);
            } else {
                System.err.println("文件不包含字典数据。网上字典？");
            }
        } else {
            System.err.println("文件不包含字典数据。网上字典？");
        }

        return counter;
    }

    private static int readDictionary(File ld2File, ByteBuffer dataRawBytes, int offsetData) throws IOException,
            FileNotFoundException, UnsupportedEncodingException {
        int counter;
        int limit = dataRawBytes.getInt(offsetData + 4) + offsetData + 8;
        int offsetIndex = offsetData + 0x1C;
        int offsetCompressedDataHeader = dataRawBytes.getInt(offsetData + 8) + offsetIndex;
        int inflatedWordsIndexLength = dataRawBytes.getInt(offsetData + 12);
        int inflatedWordsLength = dataRawBytes.getInt(offsetData + 16);
        List<Integer> deflateStreams = new ArrayList<Integer>();
        dataRawBytes.position(offsetCompressedDataHeader + 8);
        int offset = dataRawBytes.getInt();
        while (offset + dataRawBytes.position() < limit) {
            offset = dataRawBytes.getInt();
            deflateStreams.add(Integer.valueOf(offset));
        }
        ByteBuffer inflatedBytes = inflate(dataRawBytes, deflateStreams);

        String outputFile = OUT_DIR + File.separator + "output-" + ld2File.getName() + "."
                + TranslationSource.LINGOES_LD2.key;
        String[] lngs = Helper.parseLanguages(ld2File);
        counter = extract(inflatedBytes, inflatedWordsIndexLength, inflatedWordsIndexLength + inflatedWordsLength,
                outputFile, lngs[0], lngs[1], Helper.parseCategories(ld2File));
        return counter;
    }

    private static int extract(ByteBuffer inflatedBytes, int offsetDefs, int offsetXml, String outputFile, String lng1,
            String lng2, Set<String> categories) throws IOException, FileNotFoundException,
            UnsupportedEncodingException {

        BufferedWriter outputWriter = new BufferedWriter(new FileWriter(outputFile), Helper.BUFFER_SIZE);
        inflatedBytes.order(ByteOrder.LITTLE_ENDIAN);

        final int dataLen = 10;
        final int defTotal = offsetDefs / dataLen - 1;

        int[] idxData = new int[6];
        String[] defData = new String[2];

        final ArrayHelper.SensitiveStringDecoder[] encodings = detectEncodings(inflatedBytes, offsetDefs, offsetXml, defTotal, dataLen, idxData,
                defData);

        inflatedBytes.position(8);
        int counter = 0;
        int failCounter = 0;
        boolean lng1Chinese = Language.ZH.key.equalsIgnoreCase(lng1);
        boolean lng2Chinese = Language.ZH.key.equalsIgnoreCase(lng2);
        Map<String, String> languages = new FormattedTreeMap<String, String>();
        String cats = categories.toString();
        String sourceString = Helper.SEP_ATTRIBUTE + TranslationSource.TYPE_ID + TranslationSource.LINGOES_LD2.key;
        for (int i = 0; i < defTotal; i++) {
            readDefinitionData(inflatedBytes, offsetDefs, offsetXml, dataLen, encodings[0], encodings[1], idxData,
                    defData, i);

            defData[0] = defData[0].trim();
            defData[1] = defData[1].trim();

            if (defData[0].isEmpty() || defData[1].isEmpty()) {
                failCounter++;
            }
            if (failCounter > defTotal * 0.01) {
                System.err.println("??");
                System.err.println(defData[0] + " = " + defData[1]);
            }
            if (lng1Chinese) {
                defData[0] = ChineseHelper.toSimplifiedChinese(defData[0]);
            }
            if (lng2Chinese) {
                defData[1] = ChineseHelper.toSimplifiedChinese(defData[1]);
            }
            defData[1] = defData[1].replaceAll("([ ]*;[ ]*)|([ ]*,[ ]*)|([ ]*.[ ]*)", Helper.SEP_SAME_MEANING);

            if (cats.isEmpty()) {
                languages.put(lng1, defData[0] + sourceString);
                languages.put(lng2, defData[1] + sourceString);
            } else {
                // TODO
                languages.put(lng1, defData[0] + sourceString + Helper.SEP_ATTRIBUTE + Category.TYPE_ID);
                languages.put(lng2, defData[1] + sourceString + Helper.SEP_ATTRIBUTE + Category.TYPE_ID);
            }

            outputWriter.write(languages.toString());
            outputWriter.write(Helper.SEP_DEFINITION);
            outputWriter.write(cats);
            outputWriter.write(Helper.SEP_NEWLINE);
            counter++;
        }
        outputWriter.close();
        return counter;
    }

    private static final ArrayHelper.SensitiveStringDecoder[] detectEncodings(final ByteBuffer inflatedBytes,
            final int offsetWords, final int offsetXml, final int defTotal, final int dataLen, final int[] idxData,
            final String[] defData) throws UnsupportedEncodingException {
        final int test = Math.min(defTotal, 10);
        Pattern p = Pattern.compile("^.*[\\x00-\\x1f].*$");
        for (int j = 0; j < AVAIL_ENCODINGS.length; j++) {
            for (int k = 0; k < AVAIL_ENCODINGS.length; k++) {
                try {
                    readDefinitionData(inflatedBytes, offsetWords, offsetXml, dataLen, AVAIL_ENCODINGS[j],
                            AVAIL_ENCODINGS[k], idxData, defData, test);
                    System.out.println("词组编码：" + AVAIL_ENCODINGS[j]);
                    System.out.println("XML编码：" + AVAIL_ENCODINGS[k]);
                    return new ArrayHelper.SensitiveStringDecoder[] { AVAIL_ENCODINGS[j], AVAIL_ENCODINGS[k] };
                } catch (Throwable e) {
                    // ignore
                }
            }
        }
        System.err.println("自动识别编码失败！选择UTF-16LE继续。");
        return new ArrayHelper.SensitiveStringDecoder[] { AVAIL_ENCODINGS[1], AVAIL_ENCODINGS[1] };
    }

    private static void readDefinitionData(final ByteBuffer inflatedBytes, final int offsetWords, final int offsetXml,
            final int dataLen, final cn.kk.kkdict.utils.ArrayHelper.SensitiveStringDecoder wordDecoder,
            final cn.kk.kkdict.utils.ArrayHelper.SensitiveStringDecoder valueDecoder, final int[] wordIdxData,
            final String[] wordData, final int idx) throws UnsupportedEncodingException {
        getIdxData(inflatedBytes, dataLen * idx, wordIdxData);
        int lastWordPos = wordIdxData[0];
        int lastXmlPos = wordIdxData[1];
        int refs = wordIdxData[3];
        int currentWordOffset = wordIdxData[4];
        int currenXmlOffset = wordIdxData[5];
        String xml = strip(new String(valueDecoder.decode(inflatedBytes.array(), offsetXml + lastXmlPos,
                currenXmlOffset - lastXmlPos)));
        while (refs-- > 0) {
            int ref = inflatedBytes.getInt(offsetWords + lastWordPos);
            getIdxData(inflatedBytes, dataLen * ref, wordIdxData);
            lastXmlPos = wordIdxData[1];
            currenXmlOffset = wordIdxData[5];
            if (xml.isEmpty()) {
                xml = strip(new String(valueDecoder.decode(inflatedBytes.array(), offsetXml + lastXmlPos,
                        currenXmlOffset - lastXmlPos)));
            } else {
                xml = strip(new String(valueDecoder.decode(inflatedBytes.array(), offsetXml + lastXmlPos,
                        currenXmlOffset - lastXmlPos))) + Helper.SEP_LIST + xml;
            }
            lastWordPos += 4;
        }
        wordData[1] = xml;

        String word = new String(wordDecoder.decode(inflatedBytes.array(), offsetWords + lastWordPos, currentWordOffset
                - lastWordPos));
        wordData[0] = word;
    }

    private static String strip(String xml) {
        int open = 0;
        int end = 0;
        if ((open = xml.indexOf("<![CDATA[")) != -1) {
            if ((end = xml.indexOf("]]>", open)) != -1) {
                return xml.substring(open + "<![CDATA[".length(), end).replace('\t', ' ')
                        .replace(Helper.SEP_NEWLINE_CHAR, ' ').replace('\u001e', ' ').replace('\u001f', ' ');
            }
        } else if ((open = xml.indexOf("<Ô")) != -1) {
            if ((end = xml.indexOf("</Ô", open)) != -1) {
                open = xml.indexOf(">", open + 1);
                return xml.substring(open + 1, end).replace('\t', ' ').replace(Helper.SEP_NEWLINE_CHAR, ' ')
                        .replace('\u001e', ' ').replace('\u001f', ' ');
            }
        } else {
            StringBuilder sb = new StringBuilder();
            end = 0;
            open = xml.indexOf('<');
            do {
                if (open - end > 1) {
                    sb.append(xml.substring(end + 1, open));
                }
                open = xml.indexOf('<', open + 1);
                end = xml.indexOf('>', end + 1);
            } while (open != -1 && end != -1);
            return sb.toString().replace('\t', ' ').replace(Helper.SEP_NEWLINE_CHAR, ' ').replace('\u001e', ' ')
                    .replace('\u001f', ' ');
        }
        return Helper.EMPTY_STRING;
    }

    private static final void getIdxData(final ByteBuffer dataRawBytes, final int position, final int[] wordIdxData) {
        dataRawBytes.position(position);
        wordIdxData[0] = dataRawBytes.getInt();
        wordIdxData[1] = dataRawBytes.getInt();
        wordIdxData[2] = dataRawBytes.get() & 0xff;
        wordIdxData[3] = dataRawBytes.get() & 0xff;
        wordIdxData[4] = dataRawBytes.getInt();
        wordIdxData[5] = dataRawBytes.getInt();
    }

    private static final ByteBuffer inflate(final ByteBuffer dataRawBytes, final List<Integer> deflateStreams)
            throws IOException {
        int startOffset = dataRawBytes.position();
        int offset = -1;
        int lastOffset = startOffset;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Integer offsetRelative : deflateStreams) {
            offset = startOffset + offsetRelative.intValue();
            decompress(out, dataRawBytes, lastOffset, offset - lastOffset);
            lastOffset = offset;
        }
        return ByteBuffer.wrap(out.toByteArray());
    }

    private static final long decompress(final ByteArrayOutputStream out, final ByteBuffer data, final int offset,
            final int length) throws IOException {
        Inflater inflator = new Inflater();
        InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data.array(), offset, length),
                inflator, Helper.BUFFER_SIZE);
        writeInputStream(in, out);
        long bytesRead = inflator.getBytesRead();
        inflator.end();
        in.close();
        return bytesRead;
    }

    private static final void writeInputStream(final InputStream in, final OutputStream out) throws IOException {
        int len;
        while ((len = in.read(TRANSFER_BYTES)) > 0) {
            out.write(TRANSFER_BYTES, 0, len);
        }
    }

}