package com.example.squatcheck

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.squatcheck.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setUpSquatProgress()

    }

    private fun setUpSquatProgress() {
        val pieChart = binding.SquatProgress

        // Pie Chart 기본 설정
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.setExtraOffsets(5f, 10f, 5f, 5f)
        pieChart.setTouchEnabled(false)

        pieChart.holeRadius = 80f
        pieChart.transparentCircleRadius = 45f

        pieChart.maxAngle = 270f  // 최대 각도를 270도로 제한
        pieChart.rotationAngle = 135f  // 시작 각도를 조정해서 위로 향하게

        // 중앙 텍스트 설정
        pieChart.setDrawCenterText(true)
        pieChart.setCenterTextSize(16f)


        // 애니메이션
        pieChart.animateY(700)

        // === 더미 데이터 추가 ===
        val entries = ArrayList<PieEntry>()
        entries.add(PieEntry(70f))
        entries.add(PieEntry(30f))

        val dataSet = PieDataSet(entries, "")
        dataSet.setColors(Color.rgb(30,247,128), Color.rgb(200, 200, 200)) // 초록 & 회색

        val data = PieData(dataSet)
        data.setDrawValues(false)
        pieChart.data = data
        pieChart.invalidate()  // 갱신


        pieChart.getLegend().setEnabled(false);

    }
}