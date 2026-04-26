package net.minecraft.commands;

@FunctionalInterface
public interface CommandResultCallback {
    CommandResultCallback EMPTY = new CommandResultCallback() {
        @Override
        public void onResult(boolean p_309581_, int p_309698_) {
        }

        @Override
        public String toString() {
            return "<empty>";
        }
    };

    void onResult(boolean p_309554_, int p_309707_);

    default void onSuccess(int p_309552_) {
        this.onResult(true, p_309552_);
    }

    default void onFailure() {
        this.onResult(false, 0);
    }

    static CommandResultCallback chain(CommandResultCallback p_309638_, CommandResultCallback p_309688_) {
        if (p_309638_ == EMPTY) {
            return p_309688_;
        } else {
            return p_309688_ == EMPTY ? p_309638_ : (p_309648_, p_309546_) -> {
                p_309638_.onResult(p_309648_, p_309546_);
                p_309688_.onResult(p_309648_, p_309546_);
            };
        }
    }
}
