package nesoi.aysihuniks.nclaim.ui.framework;

public enum MenuType {
    CHEST_1_ROW(9),
    CHEST_2_ROWS(18),
    CHEST_3_ROWS(27),
    CHEST_4_ROWS(36),
    CHEST_5_ROWS(45),
    CHEST_6_ROWS(54);

    private final int size;

    MenuType(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}
