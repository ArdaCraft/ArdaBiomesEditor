package com.duom.ardabiomeseditor.model.polytone;

/**
 * Enumeration of Polytone asset declaration types.
 * Inline - defined directly within another asset.
 * File - defined in its own separate file.
 * Undefined - declaration type is not specified.
 */
public enum PolytoneAssetDeclarationType {

    INLINE,
    STANDALONE,
    UNDEFINED
}