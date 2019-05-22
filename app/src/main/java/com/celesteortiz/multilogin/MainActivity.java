package com.celesteortiz.multilogin;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.hdodenhof.circleimageview.CircleImageView;

//Comment 22-05-2019
//Ahora esta es mi rama master
//Resolviendo issue 001
public class MainActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 123;
    private static final int RC_FROM_GALLERY = 124;
    private static final String PROVEEDOR_DESCONOCIDO = "Proveedor desconocido";
    //Es lo que devuelve Firebase al autenticar que es por medio de correo y contrasena
    private static final String EMAIL_PROVIDER = "password";
    private static final String GOOGLE_PROVIDER = "google.com";
    private static final String FACEBOOK_PROVIDER = "facebook.com";
    private static final String PATH_PROFILE = "profile";
    private static final String PATH_USER_PHOTOS = "user_photos";
    private static final String MY_PHOTO_AUTH = "my_photo_auth";

    @BindView(R.id.imagePhotoProfile)
    CircleImageView imagePhotoProfile;
    @BindView(R.id.tvUserName)
    TextView tvUserName;
    @BindView(R.id.tvEmail)
    TextView tvEmail;
    @BindView(R.id.tvProveedor)
    TextView tvProveedor;
    /* Estos variables nos ayudaran a tener la instancia de Firebase Auth y al mismo tiempo
     * estar a la espera de cualquier cambio, ya sea un inicio o cierre de sesion */
    private FirebaseAuth mFirebaseAuth;
    //Listener called when there is a change in the authentication state
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    /*
     * Al crear la actividad lo primero que hara es lanzar una pantalla para el inicio de sesion
     * si no se tiene una sesion activa, osea si user == null
     * */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mFirebaseAuth = FirebaseAuth.getInstance();
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            /* onAuthStateChanged: Se ejecutara cada vez que se inicie o se cierre sesion
             * return FirebaseAuth firebaseAuth que contiene los datos del usuario
             * */
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                //Comprobar si existe una sesion activa
                FirebaseUser user = firebaseAuth.getCurrentUser();

                if (user != null) {
                    String displayName = user.getDisplayName();
                    String email = user.getEmail();
                    Uri p = user.getPhotoUrl();

                    //Extraer la informacion del usuario
                    onSetDataUser(displayName, email, user.getProviders() != null ? user.getProviders().get(0) : PROVEEDOR_DESCONOCIDO);

                    loadUserPhoto(user.getPhotoUrl());
                } else {
                    //Borrar los datos del usuario primero de Cache
                    onSignOutCleanup();

                    //  Inicializar los proveedores que apareceran en nuestra pantalla de inicio

                    //Configurar nuestro proveedor de Facebook
                    //Permisos de facebook: https://developers.facebook.com/docs/facebook-login/android/permissions
                    AuthUI.IdpConfig facebookIdp = new AuthUI.IdpConfig.FacebookBuilder()
                            .setPermissions(Arrays.asList("user_friends", "user_gender", "public_profile"))
                            .build();

                    /*Lanzar inicio de sesion: Email y Contrasena
                    setIsSmartLockEnabled: Sirve para recordar las contrasenas y las cuentas del usuario.
                    setAvailableProviders: Habilitar los proveedores para inicio de sesion.
                    */
                    startActivityForResult(AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setIsSmartLockEnabled(false)
                            .setTosUrl("Url de Aviso de Privacidad")
                            .setAvailableProviders(Arrays.asList(
                                    facebookIdp,
                                    new AuthUI.IdpConfig.GoogleBuilder().build(),
                                    new AuthUI.IdpConfig.EmailBuilder().build()))
                            .setTheme(R.style.GreenTheme)
                            .setLogo(R.drawable.img_multi_login)
                            .build(), RC_SIGN_IN);

                    //Va a onActivityResult y valida requestCode y ResultCode para dar acceso
                    // al usuario o denegarlo
                }
            }
        };

        //Generar KeyHash
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    "com.celesteortiz.multilogin",
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (PackageManager.NameNotFoundException e) {

        } catch (NoSuchAlgorithmException e) {

        }
    }

    /*
     * Sobre-escritura de metodo para saber el resultado despues de haber intentado iniciar sesion
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //Si ha salido to do bien despues de iniciar sesion mostrremos un mensaje de bienvenida.
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bienvenido...", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Algo fallO intente de nuevo...", Toast.LENGTH_LONG).show();

            }
        }else if(requestCode == RC_FROM_GALLERY && resultCode == RESULT_OK){

            //Una vez que se haya seleccionado la foto desde la galeria, esta se subira a
            // Firebase Storage y luego se consultara la url de la foto ya subida.
            FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
            final StorageReference referencePhoto = firebaseStorage.getReference().child(PATH_PROFILE).child(PATH_USER_PHOTOS).child(MY_PHOTO_AUTH);
            Uri selectedImageUri = data.getData();

            if(selectedImageUri != null){
                referencePhoto.putFile(selectedImageUri)
                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                referencePhoto.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri uri) {
                                        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                                        if (user != null) {
                                            UserProfileChangeRequest requestChange = new UserProfileChangeRequest.Builder()
                                                    .setPhotoUri(uri)
                                                    .build();
                                            user.updateProfile(requestChange).addOnCompleteListener(new OnCompleteListener<Void>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Void> task) {
                                                    if(task.isSuccessful()){
                                                        loadUserPhoto(user.getPhotoUrl());
                                                    }
                                                }
                                            });
                                        }
                                    }

                                });
                            }
                        })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_LONG);
                    }
                });
            }

        }
    }

    //Cargar imagen de usuario desde url
    private void loadUserPhoto(Uri photoUrl) {
        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop();

        Glide.with(getApplication())
                .load(photoUrl)
                .apply(options)
                .into(imagePhotoProfile);

    }

    /* Limpiar edit texts con los datos del usuario al cerrar la sesion
     *  en caso de que la latencia no sea la adecuada o se vaya el internet
     * */
    private void onSignOutCleanup() {
        onSetDataUser("", "", "");
    }

    /* Asignar los datos del usuario logueado a los text view de username y email
     * Y colocar el icono del proveedor con el que se logueo el usuario
     * */
    private void onSetDataUser(String username, String email, String proveedor) {
        tvUserName.setText(username);
        tvEmail.setText(email);


        //La variable contendra la referencia hacia un vector drawable, para mostrar el nombre
        //del proveedor y el icono
        int drawableRes;

        switch (proveedor) {
            case EMAIL_PROVIDER:
                drawableRes = R.drawable.ic_firebase;
                break;
            case FACEBOOK_PROVIDER:
                drawableRes = R.drawable.ic_facebook_box;
                break;
            case GOOGLE_PROVIDER:
                drawableRes = R.drawable.ic_google_plus_box;
                break;
            default:
                drawableRes = R.drawable.ic_block_helper;
                proveedor = PROVEEDOR_DESCONOCIDO;
                break;
        }
        //Setear el icono y el nombre de proveedor al textview
        tvProveedor.setCompoundDrawablesRelativeWithIntrinsicBounds(drawableRes, 0, 0, 0);
        tvProveedor.setText(proveedor);
    }

    /* Metodo sobre-escrito para Inflar nnuestro main_menu.xml
     *
     * */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /* Metodo sobre-escrito que contiene la accion de Cerrar sesion
     * en nuestro menu superior derecho
     * */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_sign_out:
                //Al cerrar sesion, volvemos al listener (mAuthStateListener = new FirebaseAuth.AuthStateListener())
                // y como usuario es null, vuelve a mostrar la pantalla de iniciar sesion
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*
     * METODOS SOBRE-ESCRITOS DE LA CLASE DE ACTIVITY
     * */
    @Override
    protected void onResume() {
        super.onResume();
        //Como buena practica se recommienda enlazar nuestro objeto de tipo Firebase Auth con la
        // Interfaz AuthStateListener en este paso del ciclo de vida de Android. De esta forma se
        // evitara un uso indebudo o excesivo del servicio.
        // En caso de tener miles de usuario esto reducira los costos
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
    }

    //Accion de seleccionar foto desde la galeria al dar click en la foto
    @OnClick(R.id.imagePhotoProfile)
    public void selectPhoto() {

        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RC_FROM_GALLERY);
    }
}
