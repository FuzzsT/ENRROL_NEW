package io.dpcaio.nativebridge

class NativeTraceBridge {
    external fun monotonicNanos(): Long
    external fun pageSize(): Int
    external fun traceMarker(label: String): Long

    companion object {
        init { System.loadLibrary("dpcaio_native_trace") }
    }
}
