package dev.swirlit.indezy.model.enums;

/** How large the logo is printed: the box it is fitted into, in points. */
public enum LogoSize {
    SMALL(110, 38),
    MEDIUM(160, 56),
    LARGE(220, 80);

    private final float width;
    private final float height;

    LogoSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }
}
