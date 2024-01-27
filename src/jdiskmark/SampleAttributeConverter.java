
package jdiskmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@Converter
public class SampleAttributeConverter implements AttributeConverter<Object, byte[]> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] convertToDatabaseColumn(Object attribute) {
        try {
            return mapper.writeValueAsBytes(attribute);
        } catch (JsonProcessingException e) {
            Logger.getLogger(SampleAttributeConverter.class.getName()).log(Level.SEVERE, null, e);
            return new byte[0];
        }
    }

    @Override
    public Object convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return new ArrayList<>();
        }
        try {
            // Deserialize directly into a List of Samples
            return mapper.readValue(dbData, mapper.getTypeFactory().constructCollectionType(ArrayList.class, Sample.class));
        } catch (IOException e) {
            Logger.getLogger(SampleAttributeConverter.class.getName()).log(Level.SEVERE, null, e);
            return new ArrayList<>();
        }        
    }
}