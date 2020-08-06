package aqua.mal;

import aqua.mal.data.ListPageItem;
import aqua.mal.data.MalAppInfo;
import aqua.mal.data.Rated;
import aqua.recommend.CFParameters;
import aqua.recommend.CFRated;
import aqua.recommend.CFUser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import io.protostuff.CodedInput;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtobufIOUtil;
import io.protostuff.Schema;
import io.protostuff.Tag;
import io.protostuff.runtime.RuntimeSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class Serialize {
    private static class RatedDeserializer extends JsonDeserializer<Rated> {
        @Override
        public Rated deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonParseException {
            int[] array = jp.readValueAs(int[].class);

            return new Rated(array[0], (byte) array[1], (byte) array[2], array.length > 3 ? (short) array[3] : 0);
        }
    }

    private static class RatedSerializer extends JsonSerializer<Rated> {
        @Override
        public void serialize(Rated rated, JsonGenerator jg, SerializerProvider provider)
                throws IOException, JsonProcessingException {
            jg.writeStartArray();

            jg.writeNumber(rated.animedbId);
            jg.writeNumber(rated.status);
            jg.writeNumber(rated.rating);
            jg.writeNumber(rated.completedDay);

            jg.writeEndArray();
        }
    }

    private static class CFRatedDeserializer extends JsonDeserializer<CFRated> {
        @Override
        public CFRated deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonParseException {
            int[] array = jp.readValueAs(int[].class);

            return new CFRated(array[0], (byte) array[1], (byte) array[2]);
        }
    }

    private static class RatedProtostuff {
        @Tag(1)
        public List<Rated> rated;

        public RatedProtostuff() {
        }

        public RatedProtostuff(List<Rated> rated) {
            this.rated = rated;
        }
    }

    private static class CFRatedProtostuff {
        @Tag(1)
        public List<CFRated> rated;

        public CFRatedProtostuff() {
        }

        public CFRatedProtostuff(List<CFRated> rated) {
            this.rated = rated;
        }
    }

    public static class CFUserInput {
        public CFRated[] animeList;
    }

    private static final ThreadLocal<LinkedBuffer> LINKED_BUFFER = new ThreadLocal<LinkedBuffer>() {
        @Override
        protected LinkedBuffer initialValue() {
            return LinkedBuffer.allocate();
        }
    };

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper XML_MAPPER = new XmlMapper();
    private static final TypeReference RATED_LIST = new TypeReference<List<Rated>>() {
    };
    private static final TypeReference CFRATED_LIST = new TypeReference<List<CFRated>>() {
    };
    private static final TypeReference ANIME_LIST_ITEM_LIST = new TypeReference<List<ListPageItem.AnimePageItem>>() {
    };
    private static final TypeReference MANGA_LIST_ITEM_LIST = new TypeReference<List<ListPageItem.MangaPageItem>>() {
    };
    private static final Schema<RatedProtostuff> RATED_SCHEMA_LIST = RuntimeSchema.getSchema(RatedProtostuff.class);
    private static final Schema<CFRatedProtostuff> CFRATED_SCHEMA_LIST = RuntimeSchema
            .getSchema(CFRatedProtostuff.class);
    private static final Schema<Rated> RATED_SCHEMA = RuntimeSchema.getSchema(Rated.class);
    private static final Schema<CFRated> CFRATED_SCHEMA = RuntimeSchema.getSchema(CFRated.class);

    static {
        SimpleModule malDecoder = new SimpleModule();
        malDecoder.addDeserializer(Rated.class, new RatedDeserializer());
        malDecoder.addSerializer(Rated.class, new RatedSerializer());
        malDecoder.addDeserializer(CFRated.class, new CFRatedDeserializer());

        JSON_MAPPER.registerModule(malDecoder);
    }

    public static List<Rated> readRatedList(InputStream is) throws IOException {
        List<Rated> rated = JSON_MAPPER.readValue(is, RATED_LIST);
        rated.sort(Rated::compareTo);
        return rated;
    }

    public static List<Rated> readRatedProtobuf(InputStream is) throws IOException {
        List<Rated> rated = new java.util.ArrayList<>();
        CodedInput parser = CodedInput.newInstance(is);
        while ((parser.readTag() >>> 3) == 1) {
            rated.add(parser.mergeObject(new Rated(), RATED_SCHEMA));
        }
        rated.sort(Rated::compareTo);
        return rated;
    }

    public static void writeRatedList(OutputStream os, List<Rated> rated) throws IOException {
        JSON_MAPPER.writeValue(os, rated);
    }

    public static void writeRatedProtobuf(OutputStream os, List<Rated> rated) throws IOException {
        LinkedBuffer linkedBuffer = LINKED_BUFFER.get();
        linkedBuffer.clear();
        ProtobufIOUtil.writeTo(os, new RatedProtostuff(rated), RATED_SCHEMA_LIST, linkedBuffer);
    }

    public static List<ListPageItem.AnimePageItem> readAnimeList(InputStream is) throws IOException {
        List<ListPageItem.AnimePageItem> rated = JSON_MAPPER.readValue(is, ANIME_LIST_ITEM_LIST);
        return rated;
    }

    public static List<ListPageItem.MangaPageItem> readMangaList(InputStream is) throws IOException {
        List<ListPageItem.MangaPageItem> rated = JSON_MAPPER.readValue(is, MANGA_LIST_ITEM_LIST);
        return rated;
    }

    public static List<CFRated> readCFRatedList(InputStream is) throws IOException {
        List<CFRated> rated = JSON_MAPPER.readValue(is, CFRATED_LIST);
        rated.sort(CFRated::compareTo);
        return rated;
    }

    public static List<CFRated> readCFRatedProtobuf(InputStream is) throws IOException {
        List<CFRated> rated = new java.util.ArrayList<>();
        CodedInput parser = CodedInput.newInstance(is);
        while ((parser.readTag() >>> 3) == 1) {
            rated.add(parser.mergeObject(new CFRated(), CFRATED_SCHEMA));
        }
        rated.sort(CFRated::compareTo);
        return rated;
    }

    public static CFUser readPartialCFUser(CFParameters cfParameters, InputStream is) throws IOException {
        CFUserInput userInput = JSON_MAPPER.readValue(is, CFUserInput.class);
        Arrays.sort(userInput.animeList, CFRated::compareTo);
        CFUser user = new CFUser();
        user.animeListIds = CFRated.packAnimeIdArray(userInput.animeList);
        user.processAfterDeserialize(cfParameters);
        return user;
    }

    public static MalAppInfo readMalAppInfo(InputStream is) throws IOException {
        return XML_MAPPER.readValue(new CleanBadXMLInputStream(is), MalAppInfo.class);
    }
}
