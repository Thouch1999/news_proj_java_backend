package com.api.v1.newssystem.model.enumerate;

/** How the text value of a system setting is read and checked (see SettingValueValidator). */
public enum SettingType {
    STRING,  // one line of plain text
    TEXT,    // several lines of plain text
    NUMBER,  // a whole number
    BOOLEAN, // true or false
    COLOR,   // #RRGGBB
    URL,     // an https address
    IMAGE    // the path of an uploaded image; changed only through the image upload endpoint
}
