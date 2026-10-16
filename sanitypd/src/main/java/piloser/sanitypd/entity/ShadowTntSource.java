package piloser.sanitypd.entity;

/**
 * Marks an entity whose explosion deals <b>psychic</b> damage instead of physical explosion damage.
 *
 * <p>It exists so the sanity-drain branch (which builds the damage source) and the overflow branch (which
 * picks the <b>death message</b>) can recognise such a source <b>by capability rather than by class</b>.
 * Adding a second psychic explosive later then needs no change in the damage code at all.
 */
public interface ShadowTntSource
{
}
