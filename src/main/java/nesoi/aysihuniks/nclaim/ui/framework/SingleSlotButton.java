package nesoi.aysihuniks.nclaim.ui.framework;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class SingleSlotButton extends Button {

    public abstract int getSlot();

    @Override
    protected final @NotNull Set<Integer> getSlots() {
        return Set.of(getSlot());
    }
}
