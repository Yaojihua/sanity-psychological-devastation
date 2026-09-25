package piloser.sanitypd;

import net.minecraft.nbt.CompoundTag;

public interface ICompoundTagSerializable
{
    void serializeNBT(CompoundTag tag);

    void deserializeNBT(CompoundTag tag);
}