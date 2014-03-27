package net.uaprom.jmorphy2;

import java.io.File;
import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.noggit.JSONParser;

import org.apache.commons.io.input.SwappedDataInputStream;

import net.uaprom.jmorphy2.dawg.PayloadsDAWG;


public class Dictionary {
    private Map<String, Object> meta;
    private WordsDAWG words;
    private Map<String,Grammeme> grammemes;
    private Paradigm[] paradigms;
    private String[] suffixes;
    private String[] paradigmPrefixes;
    private List<Tag> gramtab;
    private Map<Character,String> replaceChars;

    // TODO: load metadata
    public static final String META_FILENAME = "meta.json";
    public static final String WORDS_FILENAME = "words.dawg";
    public static final String GRAMMEMES_FILENAME = "grammemes.json";
    public static final String PARADIGMS_FILENAME = "paradigms.array";
    public static final String SUFFIXES_FILENAME = "suffixes.json";
    public static final String PARADIGM_PREFIXES_FILENAME = "paradigm-prefixes.json";
    public static final String GRAMTAB_OPENCORPORA_FILENAME = "gramtab-opencorpora-int.json";

    private static final Logger logger = LoggerFactory.getLogger(Dictionary.class);

    public static abstract class Loader {
        public abstract InputStream getStream(String filename) throws IOException;
    }

    public static class FileSystemLoader extends Loader {
        private String path;

        public FileSystemLoader(String path) {
            this.path = path;
        }

        @Override
        public InputStream getStream(String filename) throws IOException {
            return new FileInputStream(path + "/" + filename);
        }
    }

    public Dictionary(Loader loader, Map<Character,String> replaceChars) throws IOException {
        this(loader.getStream(META_FILENAME),
             loader.getStream(WORDS_FILENAME),
             loader.getStream(GRAMMEMES_FILENAME),
             loader.getStream(PARADIGMS_FILENAME),
             loader.getStream(SUFFIXES_FILENAME),
             loader.getStream(PARADIGM_PREFIXES_FILENAME),
             loader.getStream(GRAMTAB_OPENCORPORA_FILENAME),
             replaceChars);
    }

    public Dictionary(String path, Map<Character,String> replaceChars) throws IOException {
        this(new FileSystemLoader(path), replaceChars);
    }

    protected Dictionary(InputStream metaStream,
                         InputStream wordsStream,
                         InputStream grammemesStream,
                         InputStream paradigmsStream,
                         InputStream suffixesStream,
                         InputStream paradigmPrefixesStream,
                         InputStream gramtabStream,
                         Map<Character,String> replaceChars) throws IOException {
        loadMeta(metaStream);
        words = new WordsDAWG(wordsStream);
        loadGrammemes(grammemesStream);
        loadParadigms(paradigmsStream);
        loadSuffixes(suffixesStream);
        loadParadigmPrefixes(paradigmPrefixesStream);
        loadGramtab(gramtabStream);
        this.replaceChars = replaceChars;
    }

    private void loadMeta(InputStream stream) throws IOException {
        meta = new HashMap<String,Object>();
        List<List<Object>> parsed = (List<List<Object>>) parseJson(stream);
        for (List<Object> pair : parsed) {
            meta.put((String) pair.get(0), pair.get(1));
        }
    }

    private void loadGrammemes(InputStream stream) throws IOException {
        grammemes = new HashMap<String,Grammeme>();
        for (List<String> grammemeInfo : (List<List<String>>) parseJson(stream)) {
            Grammeme grammeme = new Grammeme(grammemeInfo, this);
            grammemes.put(grammeme.value, grammeme);
        }
    }

    public Grammeme getGrammeme(String value) {
        return grammemes.get(value);
    }

    public Collection<Grammeme> getAllGrammemes() {
        return grammemes.values();
    }

    private Object parseJson(InputStream stream) throws IOException {
        JSONParser parser = new JSONParser(new BufferedReader(new InputStreamReader(stream)));
        Deque<Object> stack = new LinkedList<Object>();
        Object obj = null, prevObj = null, container = null;
        int event;
        
        while ((event = parser.nextEvent()) != JSONParser.EOF) {
            switch (event) {
            case JSONParser.ARRAY_START:
                obj = new ArrayList<Object>();
                stack.addFirst(obj);
                continue;
            case JSONParser.OBJECT_START:
                obj = new HashMap<Object, Object>();
                stack.addFirst(obj);
                continue;
            case JSONParser.STRING:
                obj = parser.getString();
                break;
            case JSONParser.LONG:
                obj = parser.getLong();
                break;
            case JSONParser.NUMBER:
                obj = parser.getDouble();
                break;
            case JSONParser.BOOLEAN:
                obj = parser.getBoolean();
                break;
            case JSONParser.NULL:
                parser.getNull();
                obj = null;
                break;
            case JSONParser.ARRAY_END:
            case JSONParser.OBJECT_END:
                obj = stack.removeFirst();
                if (stack.isEmpty()) {
                    return obj;
                }
                break;
            }

            container = stack.peekFirst();
            if (container instanceof List) {
                ((List<Object>) container).add(obj);
            }
            else if (container instanceof Map) {
                if (obj != null && prevObj != null) {
                    ((Map<Object,Object>) container).put(obj, prevObj);
                }
                else {
                    prevObj = obj;
                    obj = null;
                }
            }
        }
        return obj;
    }

    private String[] readJsonStrings(InputStream stream) throws IOException {
        ArrayList<String> stringList = new ArrayList<String>();
        String[] stringArray;

        JSONParser parser = new JSONParser(new BufferedReader(new InputStreamReader(stream)));
        int event;
        while ((event = parser.nextEvent()) != JSONParser.EOF) {
            if (event == JSONParser.STRING) {
                stringList.add(parser.getString());
            }
        }
        
        stringArray = new String[stringList.size()];
        return stringList.toArray(stringArray);
    }

    private void loadParadigms(InputStream stream) throws IOException {
        DataInput paradigmsStream = new SwappedDataInputStream(stream);
        short paradigmCount = paradigmsStream.readShort();
        paradigms = new Paradigm[paradigmCount];
        for (int paraId = 0; paraId < paradigmCount; paraId++) {
            paradigms[paraId] = new Paradigm(paradigmsStream);
        }
    }

    private void loadSuffixes(InputStream stream) throws IOException {
        suffixes = readJsonStrings(stream);
    }

    private void loadParadigmPrefixes(InputStream stream) throws IOException {
        paradigmPrefixes = readJsonStrings(stream);
    }

    private void loadGramtab(InputStream stream) throws IOException {
        gramtab = new ArrayList<Tag>();
        for (String tagInfo : readJsonStrings(stream)) {
            gramtab.add(new Tag(tagInfo, this));
        }
    }

    public Tag getTag(String tagString) {
        return new Tag(tagString, this);
    }

    // private void loadReplaceChars(InputStream stream) throws IOException {
    //     int i = 0;
    //     Character c = null;
    //     for (String letter : readJsonStrings(stream)) {
    //         if (i % 2 == 0) {
    //             if (letter.length() != 1) {
    //                 throw new IOException(String.format("Replaceable string must contain only one character: '%s'", letter));
    //             }

    //             c = letter.charAt(0);
    //         }
    //         else {
    //             if (replaceChars == null) {
    //                 replaceChars = new HashMap<Character,String>();
    //             }
    //             replaceChars.put(c, letter);
    //         }

    //         i++;
    //     }
    // }

    public List<Parsed> parse(char[] word, int offset, int count) throws IOException {
        return parse(new String(word, offset, count));
    }

    public List<Parsed> parse(String word) throws IOException {
        List<String> normalForms = new ArrayList<String>();
        List<FoundParadigm> paradigms = words.similarParadigms(word, replaceChars);;
        List<Parsed> parseds = new ArrayList<Parsed>();

        for (FoundParadigm paradigm : paradigms) {
            String nf = buildNormalForm(paradigm.paradigmId,
                                        paradigm.idx,
                                        paradigm.key);
            Tag tag = buildTag(paradigm.paradigmId, paradigm.idx);
            parseds.add(new Parsed(word, tag, nf, 1.0f));
        }
        
        return parseds;
    }

    protected Tag buildTag(short paradigmId, short idx) {
        Paradigm paradigm = paradigms[paradigmId];
        int offset = paradigm.paradigm.length / 3;
        int tagId = paradigm.paradigm[offset + idx];
        return gramtab.get(tagId);
    }

    protected String buildNormalForm(short paradigmId, short idx, String word) {
        Paradigm paradigm = paradigms[paradigmId];
        int paradigmLength = paradigm.paradigm.length / 3;
        String stem = buildStem(paradigm.paradigm, idx, word);

        int prefixId = paradigm.paradigm[paradigmLength * 2 + 0] & 0xFFFF;
        int suffixId = paradigm.paradigm[0] & 0xFFFF;

        String prefix = paradigmPrefixes[prefixId];
        String suffix = suffixes[suffixId];
        
        return prefix + stem + suffix;
    }

    protected String buildStem(short[] paradigm, short idx, String word) {
        int paradigmLength = paradigm.length / 3;
        int prefixId = paradigm[paradigmLength * 2 + idx] & 0xFFFF;
        String prefix = paradigmPrefixes[prefixId];
        int suffixId = paradigm[idx] & 0xFFFF;
        String suffix = suffixes[suffixId];

        if (!suffix.equals("")) {
            return word.substring(prefix.length(), word.length() - suffix.length());
        }
        else {
            return word.substring(prefix.length());
        }
    }

    public class WordsDAWG extends PayloadsDAWG {
        public WordsDAWG(File file) throws IOException {
            super(file);
        }

        public WordsDAWG(InputStream stream) throws IOException {
            super(stream);
        }

        public List<FoundParadigm> similarParadigms(String key) throws IOException {
            return similarParadigms(key, null);
        }

        public List<FoundParadigm> similarParadigms(String key, Map<Character,String> replaceChars) throws IOException {
            List<FoundParadigm> paradigms = new ArrayList<FoundParadigm>();
            for (PayloadsDAWG.Payload item : similarItems(key, replaceChars)) {
                DataInput stream = new DataInputStream(new ByteArrayInputStream(item.value));
                short paradigmId = stream.readShort();
                short idx = stream.readShort();
                paradigms.add(new FoundParadigm(paradigmId, idx, item.key));
            }
            return paradigms;
        }
    };

    public static class Paradigm {
        public short[] paradigm;

        public Paradigm(DataInput input) throws IOException {
            short length = input.readShort();
            paradigm = new short[length];
            for (int i = 0; i < length; i++) {
                paradigm[i] = input.readShort();
            }
        }

        public short[] getParadigm() {
            return paradigm;
        }
    };

    public class FoundParadigm {
        public final short paradigmId;
        public final short idx;
        public final String key;

        public FoundParadigm(short paradigmId, short idx, String key) {
            this.paradigmId = paradigmId;
            this.idx = idx;
            this.key = key;
        }
    };
}
