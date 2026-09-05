package com.cyberday1.neoorigins.screen;

/**
 * Marker for "this screen is the origin picker", so other code can ask the
 * question with a type check instead of matching our own class names.
 *
 * <p>Only the pickers implement it — not the info or editor screens. Any future
 * picker layout must implement it too.
 */
public interface PickerScreen {
}
