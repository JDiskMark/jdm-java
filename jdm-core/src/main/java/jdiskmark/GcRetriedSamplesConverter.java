package jdiskmark;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Converter
public class GcRetriedSamplesConverter implements AttributeConverter<List<Integer>, String> {

    @Override
    public String convertToDatabaseColumn(List<Integer> list) {
        if (list == null || list.isEmpty()) {
            return null; // Database stores nothing if there are no retries
        }
        return list.stream()
                   .map(String::valueOf)
                   .collect(Collectors.joining(","));
    }

    @Override
    public List<Integer> convertToEntityAttribute(String data) {
        if (data == null || data.isBlank()) {
            return new ArrayList<>();
        }
        return Stream.of(data.split(","))
                     .map(Integer::valueOf)
                     .collect(Collectors.toCollection(ArrayList::new));
    }
}