package nesoi.aysihuniks.nclaim.model;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The extra chunks ("lands") of a claim, stored as {@code world,x,z}.
 * Mutations are mirrored into the {@link ClaimRegistry} chunk index while the claim is registered.
 */
final class LandSet extends AbstractSet<String> {

    private final Claim claim;
    private final CopyOnWriteArraySet<String> lands = new CopyOnWriteArraySet<>();

    LandSet(@NotNull Claim claim, Collection<String> initial) {
        this.claim = claim;
        if (initial != null) {
            for (String land : initial) {
                ChunkKey key = ChunkKey.parse(land);
                if (key != null) lands.add(key.toString());
            }
        }
    }

    @Override
    public boolean add(String land) {
        ChunkKey key = ChunkKey.parse(land);
        if (key == null || !lands.add(key.toString())) return false;
        if (Claim.claims.isRegistered(claim)) Claim.claims.index(claim, key.world(), key.x(), key.z());
        claim.markDirty();
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if (!(o instanceof String land) || !lands.remove(land)) return false;
        ChunkKey key = ChunkKey.parse(land);
        if (key != null && Claim.claims.isRegistered(claim)) Claim.claims.unindex(claim, key.world(), key.x(), key.z());
        claim.markDirty();
        return true;
    }

    @Override
    public boolean contains(Object o) {
        return lands.contains(o);
    }

    @Override
    public @NotNull Iterator<String> iterator() {
        Iterator<String> it = lands.iterator(); // snapshot iterator
        return new Iterator<>() {
            private String current;

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public String next() {
                current = it.next();
                return current;
            }

            @Override
            public void remove() {
                if (current == null) throw new IllegalStateException();
                LandSet.this.remove(current);
                current = null;
            }
        };
    }

    @Override
    public int size() {
        return lands.size();
    }
}
