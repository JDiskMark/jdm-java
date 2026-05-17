package jdiskmark;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * help reduce json export data size
 */
public class RoundingSerializer extends JsonSerializer<Double> {
    @Override
    public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value != null) {
            BigDecimal bd = BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
            gen.writeNumber(bd.doubleValue());
        }
    }
}