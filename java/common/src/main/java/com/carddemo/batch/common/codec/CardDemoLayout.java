package com.carddemo.batch.common.codec;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public enum CardDemoLayout {
    CVACT01Y(300), CVACT02Y(150), CVACT03Y(50), CVCUS01Y(500),
    CVTRA01Y(50), CVTRA02Y(50), CVTRA03Y(60), CVTRA04Y(60),
    CVTRA05Y(350), CVTRA06Y(350), REJTRAN(430),
    CIPAUSMY(100), CIPAUDTY(200), AUTHFRDS(204);

    private final int length;

    CardDemoLayout(int length) {
        this.length = length;
    }

    public int length() {
        return length;
    }

    public RecordLayout load() throws IOException {
        String resource = "/copybooks/" + name() + (this == AUTHFRDS ? ".dcl" : ".cpy");
        try (var stream = CardDemoLayout.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing copybook resource: " + resource);
            RecordLayout layout = RecordLayout.fromCopybook(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (layout.length() != length) throw new IllegalArgumentException("Copybook length changed: " + name());
            return layout;
        }
    }

    public FixedRecordCodec ebcdicCodec() throws IOException {
        return new FixedRecordCodec(load(), Charset.forName("IBM037"), this == CIPAUSMY);
    }
}
