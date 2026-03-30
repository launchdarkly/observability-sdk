package com.launchdarkly.observability.replay

enum class RRWebEventType(val code: Int) {
    DOM_CONTENT_LOADED(0),
    LOAD(1),
    FULL_SNAPSHOT(2),
    INCREMENTAL_SNAPSHOT(3),
    META(4),
    CUSTOM(5),
    PLUGIN(6),
}

enum class RRWebNodeType(val code: Int) {
    DOCUMENT(0),
    DOCUMENT_TYPE(1),
    ELEMENT(2),
    TEXT(3),
    CDATA(4),
    COMMENT(5),
}

enum class RRWebIncrementalSource(val code: Int) {
    MUTATION(0),
    MOUSE_MOVE(1),
    MOUSE_INTERACTION(2),
    SCROLL(3),
    VIEWPORT_RESIZE(4),
    INPUT(5),
    TOUCH_MOVE(6),
    MEDIA_INTERACTION(7),
    STYLE_SHEET_RULE(8),
    CANVAS_MUTATION(9),
    FONT(10),
    LOG(11),
    DRAG(12),
    STYLE_DECLARATION(13),
    SELECTION(14),
    ADOPTED_STYLE_SHEET(15),
    CUSTOM_ELEMENT(16),
}

enum class RRWebMouseInteraction(val code: Int) {
    MOUSE_UP(0),
    MOUSE_DOWN(1),
    CLICK(2),
    CONTEXT_MENU(3),
    DOUBLE_CLICK(4),
    FOCUS(5),
    BLUR(6),
    TOUCH_START(7),
    TOUCH_MOVE_DEPARTED(8),
    TOUCH_END(9),
    TOUCH_CANCEL(10),
}

enum class RRWebCustomDataTag(val wireValue: String) {
    CLICK("Click"),
    FOCUS("Focus"),
    VIEWPORT("Viewport"),
    RELOAD("Reload"),
    IDENTIFY("Identify"),
}
