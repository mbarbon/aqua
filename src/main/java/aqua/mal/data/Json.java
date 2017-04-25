package aqua.mal.data;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class Json {
    private static class RatedDeserializer extends JsonDeserializer<Rated> {
        @Override
        public Rated deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonParseException {
            int[] array = jp.readValueAs(int[].class);

            return new Rated(array[0], (byte) array[1], (byte) array[2],
                             array.length > 3 ? (short) array[3] : 0);
        }
    }

    private static class RatedSerializer extends JsonSerializer<Rated> {
        @Override
        public void serialize(Rated rated, JsonGenerator jg, SerializerProvider provider) throws IOException, JsonProcessingException {
            jg.writeStartArray();

            jg.writeNumber(rated.animedbId);
            jg.writeNumber(rated.status);
            jg.writeNumber(rated.rating);
            jg.writeNumber(rated.completedDay);

            jg.writeEndArray();
        }
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper XML_MAPPER = new XmlMapper();
    private static final TypeReference RATED_LIST =
        new TypeReference<List<Rated>>() {};

    static {
        SimpleModule malDecoder = new SimpleModule();
        malDecoder.addDeserializer(Rated.class, new RatedDeserializer());
        malDecoder.addSerializer(Rated.class, new RatedSerializer());

        JSON_MAPPER.registerModule(malDecoder);
    }

    public static List<Rated> readRatedList(InputStream is) throws IOException {
        List<Rated> rated = JSON_MAPPER.readValue(is, RATED_LIST);
        rated.sort(Rated::compareTo);
        return rated;
    }

    public static void writeRatedList(OutputStream os, List<Rated> rated) throws IOException {
        JSON_MAPPER.writeValue(os, rated);
    }

    public static User readUser(InputStream is) throws IOException {
        User user = JSON_MAPPER.readValue(is, User.class);
        user.animeList.sort(Rated::compareTo);
        user.computeAnimeStats();
        return user;
    }

    public static MalAppInfo readMalAppInfo(InputStream is) throws IOException {
        return XML_MAPPER.readValue(is, MalAppInfo.class);
    }
}
