package com.bonstead.pitdroid

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bonstead.pitdroid.HeaterMeter.NamedSample

class GraphFragment : Fragment(), HeaterMeter.Listener {

    private lateinit var graphView: NeonGraphView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)
        graphView = view.findViewById(R.id.neonGraph)
        return view
    }

    override fun onResume() {
        super.onResume()
        HeaterMeter.addListener(this)
        graphView.invalidate() // Force a redraw when we open the screen
    }

    override fun onPause() {
        super.onPause()
        HeaterMeter.removeListener(this)
    }

    override fun samplesUpdated(latestSample: NamedSample?) {
        // Redraw the canvas when new data arrives
        graphView.invalidate()
    }
}