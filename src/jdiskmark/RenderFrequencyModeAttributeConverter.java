package jdiskmark;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RenderFrequencyModeAttributeConverter implements AttributeConverter<RenderFrequencyMode, String> {

    @Override
    public String convertToDatabaseColumn(RenderFrequencyMode mode) {
        return (mode == null) ? null : mode.name();
    }

    @Override
    public RenderFrequencyMode convertToEntityAttribute(String dbData) {
        return (dbData == null) ? null : RenderFrequencyMode.valueOf(dbData);
    }
}