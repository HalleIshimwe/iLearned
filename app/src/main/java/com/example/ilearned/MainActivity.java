package com.example.ilearned;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;


public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavigationView;

    HomeFragment homeFragment = new HomeFragment();
    ChatbotFragment chatbotFragment = new ChatbotFragment();
    TimerFragment timerFragment = new TimerFragment();
    SettingsFragment settingsFragment = new SettingsFragment();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        bottomNavigationView = findViewById(R.id.bottomNavigationView);

        getSupportFragmentManager().beginTransaction().replace(R.id.frameLayout,homeFragment).commit();

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();


        if (id == R.id.home) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, homeFragment)
                    .commit();
            return true;
        }  else if (id == R.id.chatbot) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, chatbotFragment)
                    .commit();
            return true;

        } else if (id == R.id.timer) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, timerFragment)
                    .commit();
            return true;
        }else if (id == R.id.settings) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, settingsFragment)
                    .commit();
            return true;
        }

        return false;

    }
});

    }

}