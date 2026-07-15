package com.example.ilearned;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // Added for error logging
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable; // Added for onActivityResult
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Added Google & Firebase Auth imports needed for Google Sign-In
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;


public class SignupActivity extends AppCompatActivity {
    EditText nameEditText, emailEditText, passwordEditText,confirmPasswordEditText;
    Button signUptBtn;

    TextView loginTexview;
    ProgressBar progressBar;

    // Added Google Sign-In global variables
    Button googleSignUpBtn;
    GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //connecting to UI components
        nameEditText = findViewById(R.id.signupName);
        emailEditText = findViewById(R.id.signupEmail);
        passwordEditText = findViewById(R.id.signupPassword);
        confirmPasswordEditText = findViewById(R.id.confirmPassword);
        signUptBtn = findViewById(R.id.signupButton);
        progressBar = findViewById(R.id.signUp_progress_bar);
        loginTexview = findViewById(R.id.loginTextView);

        // Added initialization and click listener for Google Sign-In
        googleSignUpBtn = findViewById(R.id.googleSignup);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        loginTexview.setOnClickListener( view -> startActivity(new Intent(SignupActivity.this, LoginActivity.class)));

        signUptBtn.setOnClickListener(v-> createAccount());

        // Added listener to trigger Google Account selector
        googleSignUpBtn.setOnClickListener(v -> signInWithGoogle());
    }

    // Added method to open Google Sign-In Prompt
    void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    // Added method to handle the result of the Google Account picker
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account.getIdToken(), account.getDisplayName(), account.getEmail());
                }
            } catch (ApiException e) {
                Log.w("GoogleSignIn", "Google sign in failed", e);
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Added method to authenticate Google Token with Firebase and save user data
    void firebaseAuthWithGoogle(String idToken, String displayName, String email) {
        changeInProgress(true);
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    changeInProgress(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) {
                            // If it's a new Google registration, save details to Firestore
                            // Passwords aren't used for Google Auth, so we pass an empty/dummy string to your architecture
                            saveUserToFirestore(user.getUid(), displayName, email, "GoogleAuth_Account");

                            Toast.makeText(SignupActivity.this, "Welcome " + displayName, Toast.LENGTH_SHORT).show();

                            // Adjust flow based on your requirements (e.g., direct to Main Activity or stay signed out)
                            finish();
                        }
                    } else {
                        String errorMessage = (task.getException() != null)
                                ? task.getException().getLocalizedMessage()
                                : "Google Authentication failed.";
                        Toast.makeText(SignupActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    void createAccount(){
        //get user input and store in variables
        String Name = nameEditText.getText().toString();
        String email  = emailEditText.getText().toString();
        String password  = passwordEditText.getText().toString();
        String confirmPassword  = confirmPasswordEditText.getText().toString();

        boolean isValidated = validateData(email,password,confirmPassword);
        if(!isValidated){
            return;
        }

        createAccountInFirebase(Name, email,password);
    }
    void createAccountInFirebase(String name, String email, String password) {
        changeInProgress(true);
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(SignupActivity.this, task -> {
                    changeInProgress(false);

                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();

                        if (user != null) {
                            // Save user details to Firestore
                            saveUserToFirestore(user.getUid(), name, email, password);

                            user.sendEmailVerification()
                                    .addOnCompleteListener(verifyTask -> {
                                        if (verifyTask.isSuccessful()) {
                                            Toast.makeText(SignupActivity.this,
                                                    "Account created. Check your email to verify!",
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }

                        firebaseAuth.signOut();
                        finish();

                    } else {
                        String errorMessage = (task.getException() != null)
                                ? task.getException().getLocalizedMessage()
                                : "Registration failed. Please try again.";
                        Toast.makeText(SignupActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    //  Stores user details in Firestore under "users" collection
    void saveUserToFirestore(String uid, String name, String email, String password) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("name", name);
        userMap.put("email", email);
        userMap.put("password", hashPassword(password));  // Hashed, never plain-text

        db.collection("users").document(uid).set(userMap)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save profile: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    // Hashes the password using SHA-256 before storing
    String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return password; // Fallback (should never happen)
        }
    }

    void changeInProgress(boolean inProgress){
        if(inProgress){
            progressBar.setVisibility(View.VISIBLE);
            signUptBtn.setVisibility(View.GONE);
            if (googleSignUpBtn != null) googleSignUpBtn.setVisibility(View.GONE); // Included for visibility management
        }else{
            progressBar.setVisibility(View.GONE);
            signUptBtn.setVisibility(View.VISIBLE);
            if (googleSignUpBtn != null) googleSignUpBtn.setVisibility(View.VISIBLE); // Included for visibility management
        }
    }

    //validate the data that are input by user.
    boolean validateData(String email,String password,String confirmPassword){

        if(!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            emailEditText.setError("Invalid Email");
            return false;
        }
        if(password.length()<8){
            passwordEditText.setError("Password Must be at Least 8 Characters");
            return false;
        }
        if(!password.equals(confirmPassword)){
            confirmPasswordEditText.setError("Passwords do not match");
            return false;
        }
        return true;

    }

}