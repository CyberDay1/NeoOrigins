package net.minecraft.realms;

import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class RealmsObjectSelectionList<E extends ObjectSelectionList.Entry<E>> extends ObjectSelectionList<E> {
    protected RealmsObjectSelectionList(int p_120745_, int p_120746_, int p_120747_, int p_120748_) {
        super(Minecraft.getInstance(), p_120745_, p_120746_, p_120747_, p_120748_);
    }

    public void setSelectedItem(int p_120768_) {
        if (p_120768_ == -1) {
            this.setSelected(null);
        } else if (super.getItemCount() != 0) {
            this.setSelected(this.getEntry(p_120768_));
        }
    }

    public void selectItem(int p_120750_) {
        this.setSelectedItem(p_120750_);
    }

    @Override
    public int getMaxPosition() {
        return 0;
    }

    @Override
    public int getRowWidth() {
        return (int)((double)this.width * 0.6);
    }

    @Override
    public void replaceEntries(Collection<E> p_120759_) {
        super.replaceEntries(p_120759_);
    }

    @Override
    public int getItemCount() {
        return super.getItemCount();
    }

    @Override
    public int getRowTop(int p_120766_) {
        return super.getRowTop(p_120766_);
    }

    @Override
    public int getRowLeft() {
        return super.getRowLeft();
    }

    public int addEntry(E p_120757_) {
        return super.addEntry(p_120757_);
    }

    public void clear() {
        this.clearEntries();
    }
}
