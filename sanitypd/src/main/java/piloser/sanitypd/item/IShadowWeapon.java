package piloser.sanitypd.item;

/**
 * Marker interface for shadow weapons (swords and axes).
 *
 * <p>The only per-weapon difference is <b>how much psychic damage is added on hit</b>
 * (sword 8, axe 10). Both the combat logic ({@code SanityCombat}) and the interrupt check use this
 * interface, so a new shadow weapon only has to implement it and no if-else needs to change.
 */
public interface IShadowWeapon
{
    /** Psychic damage added on hit. */
    float shadowPsychicDamage();

    /**
     * Language-file id of this weapon (for example {@code shadow_sword}), used to build the tooltip
     * key {@code item.sanitypd.<id>.psychic}.
     *
     * <p>Returns {@code null} by default, which hides that tooltip line; a new weapon overrides it.
     */
    default String shadowTooltipId()
    {
        return null;
    }
}
