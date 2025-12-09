package com.duom.ardabiomeseditor.model.json;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Polytone modifier JSON model
 */
public class PolytoneModifier {

    @SerializedName("targets")
    public List<String> targets;

    @SerializedName("colormap")
    public Colormap colormap;

    public static class Colormap {
        @SerializedName("x_axis")
        public String xAxis;

        @SerializedName("y_axis")
        public String yAxis;
    }
}