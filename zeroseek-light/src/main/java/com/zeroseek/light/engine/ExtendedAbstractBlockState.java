package com.zeroseek.light.engine;

public interface ExtendedAbstractBlockState {

    boolean isConditionallyFullOpaque();

    int getOpacityIfCached();

}
