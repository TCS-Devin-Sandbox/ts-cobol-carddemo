package com.carddemo.batch.common.codec;

import java.io.IOException;
import java.nio.file.Path;

public enum CardDemoLayout {
    CVACT01Y(300), CVACT02Y(150), CVACT03Y(50), CVCUS01Y(500),
    CVTRA01Y(50), CVTRA02Y(50), CVTRA03Y(60), CVTRA04Y(60),
    CVTRA05Y(350), CVTRA06Y(350), REJTRAN(430);

    private final int length;

    CardDemoLayout(int length) {
        this.length = length;
    }

    public RecordLayout load(Path estateRoot) throws IOException {
        Path directory = estateRoot.resolve(this == REJTRAN ? "tools/parity/layouts" : "app/cpy");
        RecordLayout layout = RecordLayout.fromCopybook(directory.resolve(name() + ".cpy"));
        if (layout.length() != length) throw new IllegalArgumentException("Copybook length changed: " + name());
        return layout;
    }
}
