package net.minecraft.world.inventory;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;

public class SlotRanges {
    private static final List<SlotRange> SLOTS = Util.make(new ArrayList<>(), p_333664_ -> {
        addSingleSlot(p_333664_, "contents", 0);
        addSlotRange(p_333664_, "container.", 0, 54);
        addSlotRange(p_333664_, "hotbar.", 0, 9);
        addSlotRange(p_333664_, "inventory.", 9, 27);
        addSlotRange(p_333664_, "enderchest.", 200, 27);
        addSlotRange(p_333664_, "villager.", 300, 8);
        addSlotRange(p_333664_, "horse.", 500, 15);
        int i = EquipmentSlot.MAINHAND.getIndex(98);
        int j = EquipmentSlot.OFFHAND.getIndex(98);
        addSingleSlot(p_333664_, "weapon", i);
        addSingleSlot(p_333664_, "weapon.mainhand", i);
        addSingleSlot(p_333664_, "weapon.offhand", j);
        addSlots(p_333664_, "weapon.*", i, j);
        i = EquipmentSlot.HEAD.getIndex(100);
        j = EquipmentSlot.CHEST.getIndex(100);
        int k = EquipmentSlot.LEGS.getIndex(100);
        int l = EquipmentSlot.FEET.getIndex(100);
        int i1 = EquipmentSlot.BODY.getIndex(105);
        addSingleSlot(p_333664_, "armor.head", i);
        addSingleSlot(p_333664_, "armor.chest", j);
        addSingleSlot(p_333664_, "armor.legs", k);
        addSingleSlot(p_333664_, "armor.feet", l);
        addSingleSlot(p_333664_, "armor.body", i1);
        addSlots(p_333664_, "armor.*", i, j, k, l, i1);
        addSingleSlot(p_333664_, "horse.saddle", 400);
        addSingleSlot(p_333664_, "horse.chest", 499);
        addSingleSlot(p_333664_, "player.cursor", 499);
        addSlotRange(p_333664_, "player.crafting.", 500, 4);
    });
    public static final Codec<SlotRange> CODEC = StringRepresentable.fromValues(() -> SLOTS.toArray(new SlotRange[0]));
    private static final Function<String, SlotRange> NAME_LOOKUP = StringRepresentable.createNameLookup(SLOTS.toArray(new SlotRange[0]), p_332645_ -> p_332645_);

    private static SlotRange create(String p_332740_, int p_332819_) {
        return SlotRange.of(p_332740_, IntLists.singleton(p_332819_));
    }

    private static SlotRange create(String p_332797_, IntList p_332739_) {
        return SlotRange.of(p_332797_, IntLists.unmodifiable(p_332739_));
    }

    private static SlotRange create(String p_332671_, int... p_332804_) {
        return SlotRange.of(p_332671_, IntList.of(p_332804_));
    }

    private static void addSingleSlot(List<SlotRange> p_332716_, String p_332683_, int p_332766_) {
        p_332716_.add(create(p_332683_, p_332766_));
    }

    private static void addSlotRange(List<SlotRange> p_332790_, String p_332813_, int p_332763_, int p_332721_) {
        IntList intlist = new IntArrayList(p_332721_);

        for (int i = 0; i < p_332721_; i++) {
            int j = p_332763_ + i;
            p_332790_.add(create(p_332813_ + i, j));
            intlist.add(j);
        }

        p_332790_.add(create(p_332813_ + "*", intlist));
    }

    private static void addSlots(List<SlotRange> p_332784_, String p_332702_, int... p_332789_) {
        p_332784_.add(create(p_332702_, p_332789_));
    }

    @Nullable
    public static SlotRange nameToIds(String p_332718_) {
        return NAME_LOOKUP.apply(p_332718_);
    }

    public static Stream<String> allNames() {
        return SLOTS.stream().map(StringRepresentable::getSerializedName);
    }

    public static Stream<String> singleSlotNames() {
        return SLOTS.stream().filter(p_332670_ -> p_332670_.size() == 1).map(StringRepresentable::getSerializedName);
    }
}
