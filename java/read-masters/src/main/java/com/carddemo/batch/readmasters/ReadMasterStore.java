package com.carddemo.batch.readmasters;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.carddemo.batch.common.codec.RecordLayout;
import org.springframework.jdbc.core.JdbcTemplate;

final class ReadMasterStore {
    private final JdbcTemplate jdbc;

    ReadMasterStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Map<String, Object>> records(ReadMaster master) throws Exception {
        var layout = master.dataset.layout.load();
        String order = master.columns[0] + (layout.fields().getFirst().kind() == RecordLayout.Kind.TEXT
                ? " COLLATE \"C\"" : "");
        return jdbc.query("SELECT " + String.join(",", master.columns) + " FROM \"" + master.dataset.table
                + "\" ORDER BY " + order, (rows, rowNumber) -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (int i = 0; i < layout.fields().size(); i++) {
                        var field = layout.fields().get(i);
                        values.put(field.name(), field.kind() == RecordLayout.Kind.TEXT
                                ? rows.getString(master.columns[i]) : rows.getBigDecimal(master.columns[i]));
                    }
                    return values;
                });
    }
}
