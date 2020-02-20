package uk.ac.ebi.biosamples.model.structured.amr.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import uk.ac.ebi.biosamples.model.structured.amr.AmrPair;

import java.io.IOException;

public class AmrPairDeserializer extends StdDeserializer<AmrPair> {

    public AmrPairDeserializer() {
        super(AmrPair.class);
    }

    public AmrPairDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public AmrPair deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return p.readValueAs(AmrPair.class);
    }
}