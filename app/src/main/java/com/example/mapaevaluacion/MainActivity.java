package com.example.mapaevaluacion;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int CODIGO_PERMISO_GPS = 101;
    private MapView mapa;
    private MyLocationNewOverlay capaUbicacion;

    private final List<Marker> listaMarcadores = new ArrayList<>();

    private final double LATITUD_DESTINO = -33.4372;
    private final double LONGITUD_DESTINO = -70.6506;
    private GeoPoint ultimoPuntoSeleccionado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IConfigurationProvider config = Configuration.getInstance();
        config.setUserAgentValue("EvaluacionMapaApp/1.0 (soporte@evaluacion.cl)");
        config.setOsmdroidBasePath(getFilesDir());
        config.setOsmdroidTileCache(new File(getFilesDir(), "tiles"));
        config.load(this, PreferenceManager.getDefaultSharedPreferences(this));

        setContentView(R.layout.activity_main);

        mapa = findViewById(R.id.mapView);
        mapa.setTileSource(new XYTileSource(
                "OSMFrance",
                0, 19, 256, ".png",
                new String[]{
                        "https://a.tile.openstreetmap.fr/osmfr/",
                        "https://b.tile.openstreetmap.fr/osmfr/",
                        "https://c.tile.openstreetmap.fr/osmfr/"
                }
        ));
        mapa.setMultiTouchControls(true);

        GeoPoint puntoInicio = new GeoPoint(LATITUD_DESTINO, LONGITUD_DESTINO);
        ultimoPuntoSeleccionado = puntoInicio;
        mapa.getController().setZoom(16.0);
        mapa.getController().setCenter(puntoInicio);

        agregarMarcador(puntoInicio, "Punto Inicial");

        configurarDetectorDeToques();

        Button btnMaps = findViewById(R.id.btnAbrirGoogleMaps);
        btnMaps.setOnClickListener(v -> {
            if (ultimoPuntoSeleccionado != null) {
                abrirGoogleMaps(ultimoPuntoSeleccionado.getLatitude(), ultimoPuntoSeleccionado.getLongitude(), "Punto Mapa");
            } else {
                Toast.makeText(this, "Selecciona o crea un punto primero", Toast.LENGTH_SHORT).show();
            }
        });

        Button btnLimpiar = findViewById(R.id.btnLimpiarPuntos);
        btnLimpiar.setOnClickListener(v -> limpiarTodosLosPuntos());

        solicitarPermisosUbicacion();
    }

    private void configurarDetectorDeToques() {
        MapEventsReceiver receptorEventos = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                // Al mantener presionado, se crea un punto nuevo en esas coordenadas
                agregarMarcador(p, "Punto " + (listaMarcadores.size() + 1));
                Toast.makeText(MainActivity.this, "Punto creado. Toca el marcador para ver opciones.", Toast.LENGTH_SHORT).show();
                return true;
            }
        };

        MapEventsOverlay overlayEventos = new MapEventsOverlay(receptorEventos);
        mapa.getOverlays().add(0, overlayEventos);
    }

    private void agregarMarcador(GeoPoint punto, String titulo) {
        ultimoPuntoSeleccionado = punto;

        Marker marcador = new Marker(mapa);
        marcador.setPosition(punto);
        marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marcador.setTitle(titulo);
        marcador.setSnippet("Lat: " + String.format("%.4f", punto.getLatitude()) + " | Lon: " + String.format("%.4f", punto.getLongitude()));

        marcador.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_map));

        marcador.setOnMarkerClickListener((m, mapView) -> {
            ultimoPuntoSeleccionado = m.getPosition();
            mostrarMenuOpcionesMarcador(m);
            return true;
        });

        listaMarcadores.add(marcador);
        mapa.getOverlays().add(marcador);
        mapa.invalidate();
    }

    private void mostrarMenuOpcionesMarcador(Marker marcador) {
        String[] opciones = {"Abrir en Google Maps", "Eliminar este punto", "Cerrar"};

        new AlertDialog.Builder(this)
                .setTitle(marcador.getTitle())
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        abrirGoogleMaps(marcador.getPosition().getLatitude(), marcador.getPosition().getLongitude(), marcador.getTitle());
                    } else if (which == 1) {
                        eliminarMarcadorIndividual(marcador);
                    }
                })
                .show();
    }

    private void eliminarMarcadorIndividual(Marker marcador) {
        mapa.getOverlays().remove(marcador);
        listaMarcadores.remove(marcador);
        mapa.invalidate();
        Toast.makeText(this, "Punto eliminado", Toast.LENGTH_SHORT).show();
    }

    private void limpiarTodosLosPuntos() {
        for (Marker m : listaMarcadores) {
            mapa.getOverlays().remove(m);
        }
        listaMarcadores.clear();
        mapa.invalidate();
        Toast.makeText(this, "Todos los puntos fueron eliminados", Toast.LENGTH_SHORT).show();
    }

    private void abrirGoogleMaps(double lat, double lon, String etiqueta) {
        Uri gmmIntentUri = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(" + Uri.encode(etiqueta) + ")");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        try {
            startActivity(mapIntent);
        } catch (Exception e) {
            Uri webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon);
            Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
            startActivity(webIntent);
        }
    }

    private void solicitarPermisosUbicacion() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    CODIGO_PERMISO_GPS);
        } else {
            activarGps();
        }
    }

    private void activarGps() {
        try {
            capaUbicacion = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapa);
            capaUbicacion.enableMyLocation();
            mapa.getOverlays().add(capaUbicacion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CODIGO_PERMISO_GPS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                activarGps();
            } else {
                Toast.makeText(this, "Permiso de GPS no concedido", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapa != null) mapa.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapa != null) mapa.onPause();
    }
}