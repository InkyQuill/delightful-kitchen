package net.inkyquill.delightfulkitchen.pouch;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum FeedThreshold implements StringRepresentable {
    ANY("any"),
    HALF("half"),
    FULL("full");

    public static final Codec<FeedThreshold> CODEC = StringRepresentable.fromEnum(FeedThreshold::values);

    private final String serializedName;

    FeedThreshold(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
