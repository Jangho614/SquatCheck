package com.example.squatcheck

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.squatcheck.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment = HomeFragment()
    private val camFragment = CamFragment()
    private val profileFragment = ProfileFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 초기 프래그먼트 설정
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, homeFragment)
            .commit()

        // 바텀 네비게이션 리스너 설정
        binding.bottomMenu.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.page_1 -> homeFragment
                R.id.page_2 -> camFragment
                R.id.page_3 -> profileFragment
                else -> homeFragment
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.container, selectedFragment)
                .commit()

            true
        }
    }
}
