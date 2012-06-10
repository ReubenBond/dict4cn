/*  Copyright (c) 2010 Xiaoyun Zhu
 * 
 *  Permission is hereby granted, free of charge, to any person obtaining a copy  
 *  of this software and associated documentation files (the "Software"), to deal  
 *  in the Software without restriction, including without limitation the rights  
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell  
 *  copies of the Software, and to permit persons to whom the Software is  
 *  furnished to do so, subject to the following conditions:
 *  
 *  The above copyright notice and this permission notice shall be included in  
 *  all copies or substantial portions of the Software.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR  
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,  
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE  
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER  
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,  
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN  
 *  THE SOFTWARE.  
 */
package cn.kk.kkdict.extraction.crawl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import cn.kk.kkdict.Configuration;
import cn.kk.kkdict.Configuration.Source;
import cn.kk.kkdict.beans.DictByteBufferRow;
import cn.kk.kkdict.types.Abstract;
import cn.kk.kkdict.types.Category;
import cn.kk.kkdict.types.Language;
import cn.kk.kkdict.types.TranslationSource;
import cn.kk.kkdict.types.UriLocation;
import cn.kk.kkdict.types.WordType;
import cn.kk.kkdict.utils.ArrayHelper;
import cn.kk.kkdict.utils.ChineseHelper;
import cn.kk.kkdict.utils.Helper;

public class TermWikiCrawler {
    private static final String KEY_ABSTRACT_START = "field=\"Definition\"";
    public static final String IN_DIR = Configuration.IMPORTER_FOLDER_SELECTED_WORDS.getPath(Source.WORD_TERMWIKI);
    public static final String IN_STATUS = Configuration.IMPORTER_FOLDER_SELECTED_WORDS.getFile(Source.WORD_TERMWIKI,
            "termwiki_extractor_status.txt");
    public static final String OUT_DIR = Configuration.IMPORTER_FOLDER_EXTRACTED_CRAWLED.getPath(Source.WORD_TERMWIKI);
    public static final String OUT_DIR_FINISHED = OUT_DIR + "/finished";
    private static final String URL_TERMWIKI = "http://en.termwiki.com";
    private static final boolean DEBUG = false;

    private static final Map<String, WordType> WORD_TYPES_MAP = new HashMap<String, WordType>();
    private static final String SUFFIX_DESCRIPTION = "_abstracts";
    private static final String SUFFIX_SYNONYMS = "_redirects";
    private static final String SUFFIX_RELATED_SEEALSO = "_related_seealso";
    private static final String SUFFIX_RELATED = "_related";
    static {
        WORD_TYPES_MAP.put("noun", WordType.NOUN);
        WORD_TYPES_MAP.put("verb", WordType.VERB);
        WORD_TYPES_MAP.put("adjective", WordType.ADJECTIVE);
        WORD_TYPES_MAP.put("adverb", WordType.ADVERB);
        WORD_TYPES_MAP.put("preposition", WordType.PREPOSITION);
        WORD_TYPES_MAP.put("conjunction", WordType.CONJUNCTION);
        WORD_TYPES_MAP.put("proper noun", WordType.PROPER_NOUN);
        // added
        WORD_TYPES_MAP.put("pronoun", WordType.PRONOUN);
        WORD_TYPES_MAP.put("interjection", WordType.INTERJECTION);
        WORD_TYPES_MAP.put("article", WordType.ARTICLE);
        WORD_TYPES_MAP.put("numeral", WordType.NUMERAL);
        WORD_TYPES_MAP.put("particle", WordType.PARTICLE);
        WORD_TYPES_MAP.put("contraction", WordType.CONTRACTION);
    }

    private static final Map<String, Category> CAT_MAPPER = new TreeMap<String, Category>();

    public TermWikiCrawler() {
        new File(OUT_DIR_FINISHED).mkdirs();
    }

    static {
        final File termwikiCategories = Helper.findResource("termwiki_categories.txt");
        System.out.println("导入类型文件：" + termwikiCategories.getAbsolutePath());
        try {
            BufferedReader reader = new BufferedReader(new FileReader(termwikiCategories));
            String line;
            while (null != (line = safeReadLine(reader))) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    if (Helper.isNotEmptyOrNull(parts[0]) && Helper.isNotEmptyOrNull(parts[1])) {
                        if (DEBUG) {
                            System.out.println("类：" + parts[1] + " -> " + Category.valueOf(parts[0]).key);
                        }
                        CAT_MAPPER.put(parts[1].toUpperCase(), Category.valueOf(parts[0]));
                    } else {
                        if (DEBUG) {
                            System.out.println("类：" + parts[1] + " -> null");
                        }
                        CAT_MAPPER.put(parts[1].toUpperCase(), null);
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            System.err.println("导入错误：" + e.toString());
        }
    }

    private static final Map<String, Language> LNG_MAPPER;

    static {

        LNG_MAPPER = new HashMap<String, Language>();
        LNG_MAPPER.put("ZS", Language.ZH);
        LNG_MAPPER.put("ZT", Language.ZH);
        LNG_MAPPER.put("ZH", Language.ZH);
        LNG_MAPPER.put("AF", Language.AF);
        LNG_MAPPER.put("SQ", Language.SQ);
        LNG_MAPPER.put("AM", Language.AM);
        LNG_MAPPER.put("AR", Language.AR);
        LNG_MAPPER.put("HY", Language.HY);
        LNG_MAPPER.put("EU", Language.EU);
        LNG_MAPPER.put("BN", Language.BN);
        LNG_MAPPER.put("BS", Language.BS);
        LNG_MAPPER.put("BR", Language.BR);
        LNG_MAPPER.put("BG", Language.BG);
        LNG_MAPPER.put("KM", Language.KM);
        LNG_MAPPER.put("CA", Language.CA);
        LNG_MAPPER.put("CV", Language.CV);
        LNG_MAPPER.put("HR", Language.HR);
        LNG_MAPPER.put("CS", Language.CS);
        LNG_MAPPER.put("DA", Language.DA);
        LNG_MAPPER.put("NL", Language.NL);
        LNG_MAPPER.put("EN", Language.EN);
        LNG_MAPPER.put("UE", Language.EN);
        LNG_MAPPER.put("EO", Language.EO);
        LNG_MAPPER.put("ET", Language.ET);
        LNG_MAPPER.put("FO", Language.FO);
        LNG_MAPPER.put("TL", Language.TL);
        LNG_MAPPER.put("FI", Language.FI);
        LNG_MAPPER.put("FR", Language.FR);
        LNG_MAPPER.put("CF", Language.FR);
        LNG_MAPPER.put("GL", Language.GL);
        LNG_MAPPER.put("KA", Language.KA);
        LNG_MAPPER.put("DE", Language.DE);
        LNG_MAPPER.put("EL", Language.EL);
        LNG_MAPPER.put("GU", Language.GU);
        LNG_MAPPER.put("HA", Language.HA);
        LNG_MAPPER.put("IW", Language.IW);
        LNG_MAPPER.put("HI", Language.HI);
        LNG_MAPPER.put("HU", Language.HU);
        LNG_MAPPER.put("IS", Language.IS);
        LNG_MAPPER.put("IG", Language.IG);
        LNG_MAPPER.put("ID", Language.ID);
        LNG_MAPPER.put("GA", Language.GA);
        LNG_MAPPER.put("IT", Language.IT);
        LNG_MAPPER.put("JA", Language.JA);
        LNG_MAPPER.put("JW", Language.JV);
        LNG_MAPPER.put("KN", Language.KN);
        LNG_MAPPER.put("KK", Language.KK);
        LNG_MAPPER.put("KO", Language.KO);
        LNG_MAPPER.put("KU", Language.KU);
        LNG_MAPPER.put("LO", Language.LO);
        LNG_MAPPER.put("LA", Language.LA);
        LNG_MAPPER.put("LV", Language.LV);
        LNG_MAPPER.put("LT", Language.LT);
        LNG_MAPPER.put("MK", Language.MK);
        LNG_MAPPER.put("MS", Language.MS);
        LNG_MAPPER.put("ML", Language.ML);
        LNG_MAPPER.put("MT", Language.MT);
        LNG_MAPPER.put("MR", Language.MR);
        LNG_MAPPER.put("MC", Language.MFE);
        LNG_MAPPER.put("MN", Language.MN);
        LNG_MAPPER.put("NE", Language.NE);
        LNG_MAPPER.put("NO", Language.NO);
        LNG_MAPPER.put("NN", Language.NN);
        LNG_MAPPER.put("OR", Language.OR);
        LNG_MAPPER.put("OM", Language.OM);
        LNG_MAPPER.put("PS", Language.PS);
        LNG_MAPPER.put("FA", Language.FA);
        LNG_MAPPER.put("DR", Language.PRS);
        LNG_MAPPER.put("PL", Language.PL);
        LNG_MAPPER.put("PT", Language.PT);
        LNG_MAPPER.put("PB", Language.PT);
        LNG_MAPPER.put("RO", Language.RO);
        LNG_MAPPER.put("RM", Language.RM);
        LNG_MAPPER.put("RU", Language.RU);
        LNG_MAPPER.put("SA", Language.SA);
        LNG_MAPPER.put("GD", Language.GD);
        LNG_MAPPER.put("SR", Language.SR);
        LNG_MAPPER.put("SH", Language.SH);
        LNG_MAPPER.put("SI", Language.SI);
        LNG_MAPPER.put("SK", Language.SK);
        LNG_MAPPER.put("SL", Language.SL);
        LNG_MAPPER.put("SO", Language.SO);
        LNG_MAPPER.put("ES", Language.ES);
        LNG_MAPPER.put("XL", Language.ES);
        LNG_MAPPER.put("SW", Language.SW);
        LNG_MAPPER.put("SV", Language.SV);
        LNG_MAPPER.put("TG", Language.TG);
        LNG_MAPPER.put("TA", Language.TA);
        LNG_MAPPER.put("TH", Language.TH);
        LNG_MAPPER.put("TO", Language.TO);
        LNG_MAPPER.put("TR", Language.TR);
        LNG_MAPPER.put("TK", Language.TK);
        LNG_MAPPER.put("UG", Language.UG);
        LNG_MAPPER.put("UK", Language.UK);
        LNG_MAPPER.put("UR", Language.UR);
        LNG_MAPPER.put("VI", Language.VI);
        LNG_MAPPER.put("CY", Language.CY);
        LNG_MAPPER.put("YO", Language.YO);
    }

    // private static final Pattern PATTERN_PARAM =
    // Pattern.compile("^[\t ]*var +([a-zA-Z0-9]+) += +\"*([^ ]+?)\"* *; *$");

    private WordType wordType = null;

    public static void main(String[] args) throws IOException {
        TermWikiCrawler extractor = new TermWikiCrawler();
        extractor.extract();
    }

    public void extract() throws IOException {
        File directory = new File(IN_DIR);
        if (directory.isDirectory()) {
            System.out.print("搜索termwiki词组文件'" + IN_DIR + "' ... ");

            File[] files = directory.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith("." + TranslationSource.TERMWIKI.key) && name.startsWith("words_");
                }
            });
            System.out.println(files.length);

            long total = 0;
            for (File f : files) {
                final long start = System.currentTimeMillis();
                final int skipLines = (int) Helper.readStatsFile(IN_STATUS);
                System.out.print("分析'" + f + " [" + skipLines + "] ... ");
                final File outFile = new File(OUT_DIR, f.getName());
                final File outFileDescription = new File(OUT_DIR,
                        Helper.appendFileName(f.getName(), SUFFIX_DESCRIPTION));
                final File outFileSynonyms = new File(OUT_DIR, Helper.appendFileName(f.getName(), SUFFIX_SYNONYMS));
                final File outFileRelated = new File(OUT_DIR, Helper.appendFileName(f.getName(), SUFFIX_RELATED));
                final File outFileRelatedSeeAlso = new File(OUT_DIR, Helper.appendFileName(f.getName(),
                        SUFFIX_RELATED_SEEALSO));
                if (DEBUG) {
                    System.out.println("写出：" + outFile + "（介绍：" + outFileDescription + "，同义词： " + outFileSynonyms
                            + "，相关词：" + outFileRelated + "） 。。。");
                }
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile, skipLines > 0),
                        Helper.BUFFER_SIZE);
                BufferedOutputStream outDesc = new BufferedOutputStream(new FileOutputStream(outFileDescription,
                        skipLines > 0), Helper.BUFFER_SIZE);
                BufferedOutputStream outSyms = new BufferedOutputStream(new FileOutputStream(outFileSynonyms,
                        skipLines > 0), Helper.BUFFER_SIZE);
                BufferedOutputStream outRelsJson = new BufferedOutputStream(new FileOutputStream(outFileRelated,
                        skipLines > 0), Helper.BUFFER_SIZE);
                BufferedOutputStream outRelsSeeAlso = new BufferedOutputStream(new FileOutputStream(
                        outFileRelatedSeeAlso, skipLines > 0), Helper.BUFFER_SIZE);

                final StringBuffer cookie = new StringBuffer(512);
                cookie.append("langOptions=AF,SQ,AM,AR,HY,EU,BN,BS,BR,BG,KM,CA,ZH,ZS,ZT,CK,CV,HR,CS,DA,NL,UE,EN,EO,ET,FO,TL,FI,CF,FR,GL,KA,DE,EL,GU,HA,IW,HI,HU,IS,IG,ID,GA,IT,JA,JW,KN,KK,KO,KU,LO,LA,LV,LT,MK,MS,ML,MT,MR,MC,MN,NE,NO,NN,OR,OM,PS,DR,FA,PL,PB,PT,RO,RM,RU,SA,GD,SR,SH,SI,SK,SL,SO,XL,ES,SW,SV,TG,TA,TC,TH,BO,TO,TR,TK,UG,UK,UR,VI,CY,YO,ZU");
                HttpURLConnection conn = (HttpURLConnection) new URL(URL_TERMWIKI + "/Home").openConnection();
                Helper.appendCookies(cookie, conn);
                Helper.putConnectionHeader("Cookie", cookie.toString());
                conn.disconnect();

                int retries = 0;
                boolean success = false;
                int counter = 0;
                while (retries++ < 5 && !success) {
                    try {
                        counter = crawl(f, out, outDesc, outSyms, outRelsJson, outRelsSeeAlso, skipLines);
                        success = true;
                    } catch (Throwable e) {
                        e.printStackTrace();
                        try {
                            Thread.sleep(10 * 1000);
                        } catch (InterruptedException e1) {
                            // ignore
                        }
                    }
                }

                out.close();
                outDesc.close();
                outSyms.close();
                outRelsJson.close();
                outRelsSeeAlso.close();
                System.out.println(counter + "，用时：" + Helper.formatDuration(System.currentTimeMillis() - start));
                total += counter;
                f.renameTo(new File(OUT_DIR_FINISHED, f.getName()));
                Helper.writeStatsFile(IN_STATUS, 0L);
            }

            System.out.println("\n=====================================");
            System.out.println("成功读取了" + files.length + "个termwiki文件");
            System.out.println("总共单词：" + total);
            System.out.println("=====================================");
        }
    }

    private static enum State {
        PARSE, PARSE_DEFINITION, PARSE_DEFINITION_FULL
    }

    private int crawl(final File f, final BufferedOutputStream out, BufferedOutputStream outDesc,
            BufferedOutputStream outSyms, BufferedOutputStream outRels, BufferedOutputStream outRelsSeeAlso,
            int skipLines) throws IOException {
        if (skipLines < 0) {
            skipLines = 0;
        }
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(f), Helper.BUFFER_SIZE);
        ByteBuffer lineBB = ArrayHelper.borrowByteBufferSmall();
        DictByteBufferRow row = new DictByteBufferRow();
        int count = skipLines;
        String path = null;
        while (-1 != ArrayHelper.readLineTrimmed(in, lineBB)) {
            if (skipLines == 0) {
                row.parseFrom(lineBB);
                if (row.size() == 1) {
                    try {
                        final Language lng = Language.fromKey(ArrayHelper.toStringP(row.getLanguage(0)));
                        final String name = ArrayHelper.toStringP(row.getValue(0, 0));
                        final byte[] nameBytes = ArrayHelper.toBytesP(row.getValue(0, 0));
                        path = ArrayHelper.toStringP(row.getFirstAttributeValue(0, 0, UriLocation.TYPE_ID_BYTES));
                        // path = "/" + URLEncoder.encode(path.substring(1), Helper.CHARSET_UTF8.name());
                        final Category cat = Category.fromKey(ArrayHelper.toStringP(row.getFirstAttributeValue(0, 0,
                                Category.TYPE_ID_BYTES)));
                        if (DEBUG) {
                            System.out.println("语言：" + lng.key + "，单词：" + name + "，地址：" + path
                                    + (cat != null ? "，类别：" + cat.key : Helper.EMPTY_STRING));
                        }
                        clear();
                        final Map<String, String> params = parseMainHtml(outDesc, outSyms, outRelsSeeAlso, lng,
                                nameBytes, path);

                        // System.out.println(params.get("st_term"));
                        // System.out.println(params.get("wgCanonicalNamespace"));
                        // System.out.println(params.get("wgTitle"));

                        final String term = URLEncoder.encode(params.get("st_term"), Helper.CHARSET_UTF8.name());
                        final String ns = URLEncoder.encode(params.get("wgCanonicalNamespace"),
                                Helper.CHARSET_UTF8.name());
                        final String src = URLEncoder.encode(params.get("wgTitle"), Helper.CHARSET_UTF8.name());
                        boolean success = false;
                        int retries = 0;
                        while (!success && retries++ < 3) {
                            success = parseRelatedJson(URL_TERMWIKI + "/api.php?action=twsearch&search=" + term
                                    + "&namespace=" + ns + "&source=" + src + "&limit=50", outRels, lng, name,
                                    nameBytes, cat);
                        }

                        final String pageName = URLEncoder.encode(params.get("wgPageName"), Helper.CHARSET_UTF8.name());
                        success = false;
                        retries = 0;
                        while (!success && retries++ < 3) {
                            success = parseLanguagesAjax(URL_TERMWIKI + "/index.php/Special:LanguageBarAjax", pageName,
                                    out, lng, name, nameBytes, cat);
                        }
                        // http: //
                        // en.termwiki.com/api.php?action=twsearch&search=additifs&namespace=FR&source=additives+%E2%82%83&limit=50
                        count++;
                        if (count > 0 && count % 100 == 0) {
                            out.flush();
                            outDesc.flush();
                            outRels.flush();
                            outRelsSeeAlso.flush();
                            outSyms.flush();
                            Helper.writeStatsFile(IN_STATUS, count);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("Error: " + path);
                    }
                }
            } else {
                skipLines--;
            }
        }
        ArrayHelper.giveBack(lineBB);
        in.close();
        return count;
    }

    private void clear() {
        wordType = null;
    }

    private boolean parseLanguagesAjax(String url, String pageName, BufferedOutputStream out, Language lng,
            String name, byte[] nameBytes, Category cat) throws IOException {
        if (DEBUG) {
            System.out.println("搜索翻译：" + url);
        }
        BufferedReader reader = null;
        try {
            Helper.putConnectionHeader("X-Requested-With", "XMLHttpRequest");
            Helper.putConnectionHeader("Accept", "*/*");
            Helper.putConnectionHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            reader = new BufferedReader(new InputStreamReader(Helper.openUrlInputStream(url, true,
                    "act=makeotherlanguages&fullpagename=" + pageName), Helper.CHARSET_UTF8));
            String line;
            int idx;
            boolean first = true;
            StringBuffer sb = new StringBuffer(256);
            while (null != (line = safeReadLine(reader))) {
                if (-1 != line.indexOf("Exception:")) {
                    System.err.println("服务器方错误：" + line);
                    return false;
                }
                idx = line.indexOf(" href=\"");
                if (idx != -1) {
                    line = line.substring(idx + " href=\"".length());
                    if (line.length() > 3) {
                        final String language = line.substring(1, 3);
                        final Language targetLng = LNG_MAPPER.get(language.toUpperCase());
                        if (targetLng != null && targetLng != lng) {
                            idx = line.indexOf("\">", 3);
                            if (idx != -1) {
                                final String href = line.substring(0, idx);
                                final int translationStart = idx + "\">".length();
                                idx = line.indexOf("</a>", translationStart);
                                if (idx != -1) {
                                    String translation = line.substring(translationStart, idx);
                                    if (targetLng == Language.ZH) {
                                        translation = ChineseHelper.toSimplifiedChinese(translation);
                                    }
                                    if (DEBUG) {
                                        System.out.println(targetLng.key + "=" + translation + ", " + href);
                                    }
                                    if (first) {
                                        first = false;
                                        sb.append(lng.key);
                                        sb.append(Helper.SEP_DEFINITION);
                                        sb.append(name);
                                        if (cat != null) {
                                            sb.append(Helper.SEP_ATTRIBUTE);
                                            sb.append(Category.TYPE_ID);
                                            sb.append(cat.key);
                                        }
                                        if (wordType != null) {
                                            sb.append(Helper.SEP_ATTRIBUTE);
                                            sb.append(WordType.TYPE_ID);
                                            sb.append(wordType.key);
                                        }
                                    }
                                    sb.append(Helper.SEP_LIST);
                                    sb.append(targetLng.key);
                                    sb.append(Helper.SEP_DEFINITION);
                                    sb.append(translation);
                                    if (cat != null) {
                                        sb.append(Helper.SEP_ATTRIBUTE);
                                        sb.append(Category.TYPE_ID);
                                        sb.append(ArrayHelper.toString(cat.keyBytes));
                                    }
                                    if (wordType != null) {
                                        sb.append(Helper.SEP_ATTRIBUTE);
                                        sb.append(WordType.TYPE_ID);
                                        sb.append(wordType.key);
                                    }
                                } else {
                                    System.err.println("ajax: " + line);
                                }
                            } else {
                                System.err.println("ajax: " + line);
                            }
                        }
                    }
                }
            }
            if (!first) {
                out.write(sb.toString().getBytes(Helper.CHARSET_UTF8));
                out.write(Helper.SEP_NEWLINE_BYTES);
            }
        } finally {
            Helper.close(reader);
        }
        return true;
    }

    private boolean parseRelatedJson(String url, BufferedOutputStream outRelsJson, Language lng, String name,
            byte[] nameBytes, Category cat) throws IOException {
        if (DEBUG) {
            System.out.println("搜索相关词汇：" + url);
        }
        BufferedReader reader = null;
        try {
            Helper.putConnectionHeader("X-Requested-With", "XMLHttpRequest");
            Helper.putConnectionHeader("Accept", "application/json, text/javascript, */*");
            Helper.putConnectionHeader("Content-Type", "application/x-www-form-urlencoded");

            reader = new BufferedReader(new InputStreamReader(Helper.openUrlInputStream(url), Helper.CHARSET_UTF8));
            String line;
            int idx;
            while (null != (line = safeReadLine(reader))) {
                if (-1 != line.indexOf("Exception:")) {
                    System.err.println("服务器方错误：" + line);
                    return false;
                } else if ((idx = line.indexOf("\"title\":")) != -1) {
                    line = line.substring(idx + "\"title\":".length());
                    final String[] titles = line.split("\"title\":");
                    boolean first = true;
                    for (String t : titles) {
                        idx = t.indexOf("\",\"industry\":\"");
                        if (idx != -1) {
                            final String title = Helper.unescapeCode(t.substring(1, idx));
                            if (!name.equals(title)) {
                                final int catStart = idx + "\",\"industry\":\"".length();
                                idx = t.indexOf("\",\"", catStart);
                                final String category = Helper.unescapeCode(t.substring(catStart, idx));
                                final Category targetCat = CAT_MAPPER.get(category.toUpperCase());
                                final boolean containsCat = CAT_MAPPER.containsKey(category.toUpperCase());
                                if (DEBUG) {
                                    if (targetCat != null) {
                                        System.out.println("title: " + title + ", cat: " + targetCat.key);
                                    } else {
                                        if (!containsCat) {
                                            System.out.println("title: " + title + ", ?cat?: " + category);
                                        }
                                    }
                                }
                                if (first) {
                                    first = false;
                                    outRelsJson.write(lng.keyBytes);
                                    outRelsJson.write(Helper.SEP_DEFINITION_BYTES);
                                    outRelsJson.write(nameBytes);
                                    if (cat != null) {
                                        outRelsJson.write(Helper.SEP_ATTRS_BYTES);
                                        outRelsJson.write(Category.TYPE_ID_BYTES);
                                        outRelsJson.write(cat.keyBytes);
                                    }
                                }
                                outRelsJson.write(Helper.SEP_WORDS_BYTES);
                                outRelsJson.write(title.getBytes(Helper.CHARSET_UTF8));
                                if (targetCat != null) {
                                    outRelsJson.write(Helper.SEP_ATTRS_BYTES);
                                    outRelsJson.write(Category.TYPE_ID_BYTES);
                                    outRelsJson.write(targetCat.keyBytes);
                                }
                            }
                        }
                    }
                    if (!first) {
                        outRelsJson.write(Helper.SEP_NEWLINE_BYTES);
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            System.err.println("搜索相关词汇错误：" + t.toString());
            return false;
        } finally {
            Helper.close(reader);
        }
        return true;
    }

    private Map<String, String> parseMainHtml(BufferedOutputStream outDesc, BufferedOutputStream outSyms,
            BufferedOutputStream outRelsSeeAlso, final Language lng, final byte[] nameBytes, final String path)
            throws MalformedURLException, IOException {
        Helper.putConnectionHeader("X-Requested-With", null);
        Helper.putConnectionHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        Helper.putConnectionHeader("Content-Type", null);

        final Map<String, String> params = new HashMap<String, String>();
        HttpURLConnection conn = Helper.getUrlConnection(URL_TERMWIKI + path);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), Helper.CHARSET_UTF8));
        String line;
        State state = State.PARSE;
        StringBuffer sb = new StringBuffer();
        int idx;
        HTML: while (null != (line = safeReadLine(reader))) {
            switch (state) {
            case PARSE_DEFINITION:
            case PARSE_DEFINITION_FULL:
                if ((idx = line.indexOf("</p>")) != -1) {
                    state = appendDefinition(state, sb, line.substring(0, idx));
                    final String abstractText = sb.toString().trim().replaceAll("[\\t ]+", " ");
                    if (DEBUG) {
                        System.out.println("def: " + abstractText);
                    }
                    outDesc.write(lng.keyBytes);
                    outDesc.write(Helper.SEP_DEFINITION_BYTES);
                    outDesc.write(nameBytes);
                    outDesc.write(Helper.SEP_ATTRS_BYTES);
                    outDesc.write(Abstract.TYPE_ID_BYTES);
                    outDesc.write(abstractText.getBytes(Helper.CHARSET_UTF8));
                    outDesc.write(Helper.SEP_NEWLINE_BYTES);
                    state = State.PARSE;
                } else {
                    state = appendDefinition(state, sb, line);
                }
                break;
            case PARSE:
            default:
                // System.out.println(line);
                if ((idx = line.indexOf(KEY_ABSTRACT_START)) != -1) {
                    state = State.PARSE_DEFINITION;
                    state = appendDefinition(state, sb, line.substring(idx + KEY_ABSTRACT_START.length()));
                } else if ((idx = line.indexOf(">Part of Speech:<")) != -1) {
                    final String partOfSpeech = Helper.substringBetweenLast(line, "</span>", "<br");
                    if (Helper.isNotEmptyOrNull(partOfSpeech)) {
                        wordType = WORD_TYPES_MAP.get(partOfSpeech);
                        if (wordType == null && !WORD_TYPES_MAP.containsKey(partOfSpeech)) {
                            System.err.println("未知词类：" + partOfSpeech);
                        }
                    }
                } else if ((idx = line.indexOf(">Synonym(s):<")) != -1) {
                    String synonyms = Helper.substringBetweenLast(line, "</span>", "<br");
                    if (Helper.isNotEmptyOrNull(synonyms)) {
                        if (DEBUG) {
                            System.out.println("syms: " + synonyms);
                        }
                        if ((idx = synonyms.indexOf(" href=\"")) != -1) {
                            final int hrefStart = idx + " href=\"".length();
                            synonyms = synonyms.substring(hrefStart);
                            final String[] syms = synonyms.split(" href=\"");
                            outSyms.write(lng.keyBytes);
                            outSyms.write(Helper.SEP_DEFINITION_BYTES);
                            outSyms.write(nameBytes);
                            for (String s : syms) {
                                final String href = s.substring(0, (idx = s.indexOf("\">")));
                                final int titleStart = idx + "\">".length();
                                final String title = s.substring(titleStart, s.indexOf("</a>", titleStart));
                                outSyms.write(Helper.SEP_WORDS_BYTES);
                                outSyms.write(title.getBytes(Helper.CHARSET_UTF8));
                                outSyms.write(Helper.SEP_ATTRS_BYTES);
                                outSyms.write(UriLocation.TYPE_ID_BYTES);
                                outSyms.write(href.getBytes(Helper.CHARSET_UTF8));
                            }
                            outSyms.write(Helper.SEP_NEWLINE_BYTES);
                        }
                    }
                } else if ((idx = line.indexOf(">See Also:<")) != -1) {
                    String seeAlsos = Helper.substringBetweenLast(line, "</span>", "<br");
                    if (Helper.isNotEmptyOrNull(seeAlsos)) {
                        System.out.println("see also: " + seeAlsos);
                        if ((idx = seeAlsos.indexOf(" href=\"")) != -1) {
                            final int hrefStart = idx + " href=\"".length();
                            seeAlsos = seeAlsos.substring(hrefStart);
                            final String[] sees = seeAlsos.split(" href=\"");
                            outRelsSeeAlso.write(lng.keyBytes);
                            outRelsSeeAlso.write(Helper.SEP_DEFINITION_BYTES);
                            outRelsSeeAlso.write(nameBytes);
                            for (String s : sees) {
                                final String href = s.substring(0, (idx = s.indexOf("\">")));
                                final int titleStart = idx + "\">".length();
                                final String title = s.substring(titleStart, s.indexOf("</a>", titleStart));
                                outRelsSeeAlso.write(Helper.SEP_WORDS_BYTES);
                                outRelsSeeAlso.write(title.getBytes(Helper.CHARSET_UTF8));
                                outRelsSeeAlso.write(Helper.SEP_ATTRS_BYTES);
                                outRelsSeeAlso.write(UriLocation.TYPE_ID_BYTES);
                                outRelsSeeAlso.write(href.getBytes(Helper.CHARSET_UTF8));
                            }
                            outRelsSeeAlso.write(Helper.SEP_NEWLINE_BYTES);
                        }
                    }
                } else if (line.endsWith(";")) {
                    // find parameter
                    line = line.trim();
                    if (line.startsWith("var ") && (line.indexOf('"') != -1 || line.indexOf('\'') != -1)) {
                        String[] parts = line.split(" ");
                        if (parts.length > 3) {
                            final String key = parts[1];
                            String val = parts[3];
                            if (val.length() > 0 && val.indexOf('"') == 0) {
                                val = Helper.substringBetweenEnclose(line, "\"", "\"");
                            } else if (val.length() > 0 && val.indexOf('\'') == 0) {
                                val = Helper.substringBetweenEnclose(line, "'", "'");
                            }
                            if (val != null) {
                                val = Helper.unescapeCode(val);
                                // System.out.println(key + "=" + val);
                                params.put(key, val);
                            }
                        }
                    }
                } else if (line.indexOf("id=\"recently_talks_id\"") != -1) {
                    break HTML;
                }
            }
        }
        reader.close();
        return params;
    }

    private static String safeReadLine(BufferedReader reader) throws IOException {
        try {
            return reader.readLine();
        } catch (IOException e) {
            return Helper.EMPTY_STRING;
        }
    }

    private static final State appendDefinition(final State state, final StringBuffer sb, final String line) {
        if (State.PARSE_DEFINITION == state) {
            if (sb.length() > Abstract.MAX_ABSTRACT_CHARS) {
                sb.append(Helper.SEP_ETC);
                return State.PARSE_DEFINITION_FULL;
            } else {
                sb.append(Helper.unescapeHtml(Helper.stripHtmlText(line, true)));
            }
        }
        return state;
    }
}
