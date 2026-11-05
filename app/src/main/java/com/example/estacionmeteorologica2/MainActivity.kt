
package com.example.estacionmeteorologica2

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.estacionmeteorologica2.ui.theme.EstacionMeteorologica2Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// COLORES DE LA APLICACION
// ==========================================

private val AzulOscuro = Color(0xFF14283F)
private val AzulMedio = Color(0xFF243E5B)
private val Turquesa = Color(0xFF12B8A6)
private val Fondo = Color(0xFFF3F6FB)
private val Blanco = Color(0xFFFFFFFF)
private val TextoPrincipal = Color(0xFF1B2C43)
private val TextoSecundario = Color(0xFF718198)
private val Rojo = Color(0xFFE15D65)
private val Verde = Color(0xFF22A875)
private val AzulSensor = Color(0xFF4285F4)
private val Naranja = Color(0xFFF0A33B)

// ==========================================
// MODELO DE DATOS
// ==========================================

private data class LecturaAmbiental(
    val temperatura: Double,
    val humedad: Double,
    val nivelAgua: Double,
    val capturadaEn: Long
)

// ==========================================
// COMUNICACION CON PACKET TRACER
// ==========================================

private suspend fun solicitarDispositivo(
    ruta: String
): String = withContext(Dispatchers.IO) {

    val conexion = URL(
        "http://10.0.2.2:8765$ruta"
    ).openConnection() as HttpURLConnection

    conexion.requestMethod = "GET"
    conexion.connectTimeout = 5000
    conexion.readTimeout = 5000

    try {

        if (conexion.responseCode == 200) {

            conexion.inputStream
                .bufferedReader()
                .use {
                    it.readText().trim()
                }

        } else {

            throw Exception(
                "Error HTTP: ${conexion.responseCode}"
            )
        }

    } finally {
        conexion.disconnect()
    }
}

// ==========================================
// VALIDAR NUMEROS
// ==========================================

private fun convertirNumero(
    respuesta: String,
    nombre: String
): Double {

    val numero = respuesta.toDoubleOrNull()

    if (numero == null || !numero.isFinite()) {
        throw Exception(
            "$nombre no válido: $respuesta"
        )
    }

    return numero
}

// ==========================================
// INTERPRETAR HUMEDAD Y NIVEL DE AGUA
// ==========================================

private fun interpretarSensores(
    respuesta: String
): Pair<Double, Double> {

    val valores = respuesta
        .split(";")
        .mapNotNull { parte ->

            val campos = parte.split(
                "=",
                limit = 2
            )

            if (campos.size == 2) {
                campos[0].trim() to campos[1].trim()
            } else {
                null
            }
        }
        .toMap()

    val humedad = convertirNumero(
        valores["humedad"] ?: "",
        "Humedad"
    )

    val nivelAgua = convertirNumero(
        valores["nivelAgua"] ?: "",
        "Nivel de agua"
    )

    return Pair(humedad, nivelAgua)
}

// ==========================================
// OBTENER LECTURAS DE PACKET TRACER
// ==========================================

private suspend fun obtenerLecturaCompleta():
        LecturaAmbiental {

    val respuestaTemperatura =
        solicitarDispositivo("/temperatura")

    val temperatura = convertirNumero(
        respuestaTemperatura,
        "Temperatura"
    )

    val respuestaSensores =
        solicitarDispositivo("/sensores")

    val sensores =
        interpretarSensores(respuestaSensores)

    return LecturaAmbiental(
        temperatura = temperatura,
        humedad = sensores.first,
        nivelAgua = sensores.second,
        capturadaEn = System.currentTimeMillis()
    )
}

// ==========================================
// GUARDAR LECTURA EN EL CELULAR
// ==========================================

private fun guardarLecturaLocal(
    contexto: Context,
    uid: String,
    lectura: LecturaAmbiental
) {

    val preferencias =
        contexto.getSharedPreferences(
            "estacion_$uid",
            Context.MODE_PRIVATE
        )

    preferencias.edit()
        .putString(
            "temperatura",
            lectura.temperatura.toString()
        )
        .putString(
            "humedad",
            lectura.humedad.toString()
        )
        .putString(
            "nivelAgua",
            lectura.nivelAgua.toString()
        )
        .putLong(
            "capturadaEn",
            lectura.capturadaEn
        )
        .apply()
}

// ==========================================
// RECUPERAR LECTURA LOCAL
// ==========================================

private fun recuperarLecturaLocal(
    contexto: Context,
    uid: String
): LecturaAmbiental? {

    if (uid.isBlank()) return null

    val preferencias =
        contexto.getSharedPreferences(
            "estacion_$uid",
            Context.MODE_PRIVATE
        )

    val temperatura = preferencias
        .getString("temperatura", null)
        ?.toDoubleOrNull()
        ?: return null

    val humedad = preferencias
        .getString("humedad", null)
        ?.toDoubleOrNull()
        ?: return null

    val nivelAgua = preferencias
        .getString("nivelAgua", null)
        ?.toDoubleOrNull()
        ?: return null

    val fecha = preferencias.getLong(
        "capturadaEn",
        0L
    )

    if (
        !temperatura.isFinite() ||
        !humedad.isFinite() ||
        !nivelAgua.isFinite() ||
        fecha <= 0L
    ) {
        return null
    }

    return LecturaAmbiental(
        temperatura = temperatura,
        humedad = humedad,
        nivelAgua = nivelAgua,
        capturadaEn = fecha
    )
}

// ==========================================
// FORMATO DE NUMEROS Y FECHAS
// ==========================================

private fun formatoNumero(
    numero: Double
): String {

    return String.format(
        Locale.US,
        "%.2f",
        numero
    ).replace(".", ",")
}

private fun formatoFecha(
    fecha: Long
): String {

    return SimpleDateFormat(
        "dd/MM/yyyy HH:mm:ss",
        Locale.forLanguageTag("es-CL")
    ).format(Date(fecha))
}

// ==========================================
// COMPONENTES VISUALES REUTILIZABLES
// ==========================================

@Composable
private fun TituloSeccion(
    titulo: String,
    subtitulo: String = ""
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        Text(
            text = titulo,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextoPrincipal
        )

        if (subtitulo.isNotEmpty()) {

            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )
        }
    }
}

@Composable
private fun TarjetaBase(
    contenido: @Composable ColumnScope.() -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Blanco
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
            content = contenido
        )
    }
}

@Composable
private fun TarjetaSensor(
    nombre: String,
    valor: String,
    icono: String,
    color: Color,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Blanco
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(43.dp)
                    .background(
                        color.copy(alpha = 0.12f),
                        RoundedCornerShape(13.dp)
                    ),

                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = icono,
                    style =
                        MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = nombre,
                style =
                    MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )

            Text(
                text = valor,
                style =
                    MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal
            )
        }
    }
}

@Composable
private fun BotonPrincipal(
    texto: String,
    cargando: Boolean = false,
    habilitado: Boolean = true,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        enabled = habilitado && !cargando,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),

        shape = RoundedCornerShape(15.dp),

        colors = ButtonDefaults.buttonColors(
            containerColor = Turquesa,
            contentColor = Blanco
        )
    ) {

        if (cargando) {

            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Blanco,
                strokeWidth = 2.dp
            )

            Spacer(Modifier.width(10.dp))
        }

        Text(
            text = texto,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TarjetaControl(
    titulo: String,
    icono: String,
    activo: Boolean,
    verificado: Boolean,
    cargando: Boolean,
    mensaje: String,
    onClick: () -> Unit
) {

    TarjetaBase {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Turquesa.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = icono,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {

                Text(
                    text = titulo,
                    fontWeight = FontWeight.Bold,
                    color = TextoPrincipal
                )

                Text(
                    text = if (!verificado) {
                        "Estado sin verificar"
                    } else if (activo) {
                        "Activado"
                    } else {
                        "Desactivado"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activo && verificado) {
                        Verde
                    } else {
                        TextoSecundario
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        if (!verificado) {
                            TextoSecundario
                        } else if (activo) {
                            Verde
                        } else {
                            Rojo
                        },
                        CircleShape
                    )
            )
        }

        Button(
            onClick = onClick,
            enabled = !cargando,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),

            shape = RoundedCornerShape(14.dp),

            colors = ButtonDefaults.buttonColors(
                containerColor = if (activo) {
                    Rojo
                } else {
                    Turquesa
                },
                contentColor = Blanco
            )
        ) {

            Text(
                text = if (cargando) {
                    "Enviando comando..."
                } else if (activo) {
                    "Desactivar $titulo"
                } else {
                    "Activar $titulo"
                },
                fontWeight = FontWeight.SemiBold
            )
        }

        if (mensaje.isNotEmpty()) {

            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )
        }
    }
}

// ==========================================
// ACTIVIDAD PRINCIPAL
// ==========================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContent {

            EstacionMeteorologica2Theme {

                val auth = remember {
                    FirebaseAuth.getInstance()
                }

                var sesionIniciada by remember {
                    mutableStateOf(
                        auth.currentUser != null
                    )
                }

                if (sesionIniciada) {

                    PantallaEstacion(
                        onCerrarSesion = {

                            auth.signOut()
                            sesionIniciada = false
                        }
                    )

                } else {

                    PantallaLogin(
                        onAccesoCorrecto = {
                            sesionIniciada = true
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// PANTALLA DE INICIO DE SESION
// ==========================================

@Composable
fun PantallaLogin(
    onAccesoCorrecto: () -> Unit
) {

    val auth = remember {
        FirebaseAuth.getInstance()
    }

    var correo by remember {
        mutableStateOf("")
    }

    var clave by remember {
        mutableStateOf("")
    }

    var mensaje by remember {
        mutableStateOf("")
    }

    var cargando by remember {
        mutableStateOf(false)
    }

    fun autenticar(crearCuenta: Boolean) {

        if (correo.isBlank() || clave.length < 6) {

            mensaje =
                "Ingresa un correo y una contraseña de al menos 6 caracteres."

            return
        }

        cargando = true
        mensaje = ""

        val tarea = if (crearCuenta) {

            auth.createUserWithEmailAndPassword(
                correo.trim(),
                clave
            )

        } else {

            auth.signInWithEmailAndPassword(
                correo.trim(),
                clave
            )
        }

        tarea.addOnCompleteListener { resultado ->

            cargando = false

            if (resultado.isSuccessful) {

                onAccesoCorrecto()

            } else {

                mensaje =
                    resultado.exception?.localizedMessage
                        ?: "Error de autenticación"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),

        verticalArrangement =
            Arrangement.spacedBy(20.dp)
    ) {

        Spacer(Modifier.height(36.dp))

        // ENCABEZADO ELEGANTE

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    AzulOscuro,
                    RoundedCornerShape(26.dp)
                )
                .padding(26.dp),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = "🌤️",
                style =
                    MaterialTheme.typography.displayMedium
            )

            Text(
                text = "ESTACIÓN IoT",
                color = Color(0xFF79E3D6),
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Bienvenido a tu estación meteorológica",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Blanco
            )

            Text(
                text = "Monitorea el ambiente y controla tus dispositivos desde un solo lugar.",
                color = Color(0xFFD2DDE8)
            )
        }

        TituloSeccion(
            titulo = "Iniciar sesión",
            subtitulo = "Accede a tu panel de monitoreo"
        )

        TarjetaBase {

            OutlinedTextField(
                value = correo,
                onValueChange = {
                    correo = it
                },
                label = {
                    Text("Correo electrónico")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = clave,
                onValueChange = {
                    clave = it
                },
                label = {
                    Text("Contraseña")
                },
                visualTransformation =
                    PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            BotonPrincipal(
                texto = "Iniciar sesión",
                cargando = cargando,
                onClick = {
                    autenticar(false)
                }
            )

            OutlinedButton(
                onClick = {
                    autenticar(true)
                },
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {

                Text("Crear una cuenta")
            }

            if (mensaje.isNotEmpty()) {

                Text(
                    text = mensaje,
                    color = Rojo
                )
            }
        }

        Text(
            text = "Estación Meteorológica IoT · Panel de monitoreo",
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario
        )
    }
}

// ==========================================
// PANTALLA PRINCIPAL
// ==========================================

@Composable
fun PantallaEstacion(
    onCerrarSesion: () -> Unit
) {

    val contexto =
        androidx.compose.ui.platform.LocalContext.current
            .applicationContext

    val usuario = FirebaseAuth
        .getInstance()
        .currentUser

    val uid = usuario?.uid ?: ""

    val scope = rememberCoroutineScope()

    // ======================================
    // LECTURAS AMBIENTALES
    // ======================================

    var ultimaLectura by remember(uid) {
        mutableStateOf(
            recuperarLecturaLocal(
                contexto,
                uid
            )
        )
    }

    var consultando by remember {
        mutableStateOf(false)
    }

    var mensajeSensores by remember {
        mutableStateOf("")
    }

    // ======================================
    // ESTADO DE FIREBASE
    // ======================================

    var firebaseConectado by remember {
        mutableStateOf<Boolean?>(null)
    }

    var mensajeGuardado by remember {
        mutableStateOf("")
    }

    // ======================================
    // LED
    // ======================================

    var ledActivo by remember {
        mutableStateOf(false)
    }

    var ledVerificado by remember {
        mutableStateOf(false)
    }

    var controlandoLed by remember {
        mutableStateOf(false)
    }

    var mensajeLed by remember {
        mutableStateOf("")
    }

    // ======================================
    // ALARMA
    // ======================================

    var buzzerActivo by remember {
        mutableStateOf(false)
    }

    var buzzerVerificado by remember {
        mutableStateOf(false)
    }

    var controlandoBuzzer by remember {
        mutableStateOf(false)
    }

    var mensajeBuzzer by remember {
        mutableStateOf("")
    }

    // ======================================
    // OBSERVAR CONEXION FIREBASE
    // ======================================

    DisposableEffect(uid) {

        val referencia = FirebaseDatabase
            .getInstance()
            .getReference(".info/connected")

        val listener = object : ValueEventListener {

            override fun onDataChange(
                snapshot: DataSnapshot
            ) {

                firebaseConectado =
                    snapshot.getValue(Boolean::class.java)
                        ?: false
            }

            override fun onCancelled(
                error: DatabaseError
            ) {

                firebaseConectado = false
            }
        }

        referencia.addValueEventListener(listener)

        onDispose {

            referencia.removeEventListener(listener)
        }
    }

    // ======================================
    // ESTADO INICIAL DE LED Y ALARMA
    // ======================================

    LaunchedEffect(uid) {

        try {

            val estado =
                solicitarDispositivo("/led/estado")

            if (
                estado == "ENCENDIDO" ||
                estado == "APAGADO"
            ) {

                ledActivo = estado == "ENCENDIDO"
                ledVerificado = true
            }

        } catch (e: Exception) {

            mensajeLed =
                "No se pudo consultar el estado inicial."
        }

        try {

            val estado =
                solicitarDispositivo("/buzzer/estado")

            if (
                estado == "ACTIVADA" ||
                estado == "DESACTIVADA"
            ) {

                buzzerActivo =
                    estado == "ACTIVADA"

                buzzerVerificado = true
            }

        } catch (e: Exception) {

            mensajeBuzzer =
                "No se pudo consultar el estado inicial."
        }
    }

    // ======================================
    // INTERFAZ DEL DASHBOARD
    // ======================================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),

        verticalArrangement =
            Arrangement.spacedBy(18.dp)
    ) {

        // ENCABEZADO PRINCIPAL

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    AzulOscuro,
                    RoundedCornerShape(25.dp)
                )
                .padding(23.dp),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = "ESTACIÓN METEOROLÓGICA",
                color = Color(0xFF75E0D4),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Panel de monitoreo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Blanco
            )

            Text(
                text = "Controla tu estación y consulta las condiciones ambientales en tiempo real bajo demanda.",
                color = Color(0xFFD6E2ED),
                style = MaterialTheme.typography.bodyMedium
            )

            Surface(
                color = AzulMedio,
                shape = RoundedCornerShape(12.dp)
            ) {

                Text(
                    text = "🌐  Cisco Packet Tracer + Firebase",
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 9.dp
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Blanco
                )
            }
        }

        // ==================================
        // ESTADO DE CONEXION
        // ==================================

        TarjetaBase {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (firebaseConectado == true) {
                                Verde
                            } else {
                                Rojo
                            },
                            CircleShape
                        )
                )

                Spacer(Modifier.width(10.dp))

                Column {

                    Text(
                        text = "Estado de conexión",
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal
                    )

                    Text(
                        text = when (firebaseConectado) {

                            true ->
                                "Conectado a Firebase"

                            false ->
                                "Sin conexión con Firebase"

                            null ->
                                "Comprobando conexión..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario
                    )
                }
            }
        }

        // ==================================
        // SENSORES
        // ==================================

        TituloSeccion(
            titulo = "Datos ambientales",
            subtitulo = "Última lectura disponible"
        )

        val lectura = ultimaLectura

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            TarjetaSensor(
                nombre = "Temperatura",
                valor = if (lectura != null) {
                    "${formatoNumero(lectura.temperatura)} °C"
                } else {
                    "-- °C"
                },
                icono = "🌡️",
                color = Naranja,
                modifier = Modifier.weight(1f)
            )

            TarjetaSensor(
                nombre = "Humedad",
                valor = if (lectura != null) {
                    "${formatoNumero(lectura.humedad)} %"
                } else {
                    "-- %"
                },
                icono = "💧",
                color = AzulSensor,
                modifier = Modifier.weight(1f)
            )
        }

        TarjetaSensor(
            nombre = "Nivel de agua",
            valor = if (lectura != null) {
                formatoNumero(lectura.nivelAgua)
            } else {
                "--"
            },
            icono = "🌊",
            color = Turquesa,
            modifier = Modifier.fillMaxWidth()
        )

        if (lectura != null) {

            Text(
                text = "Última medición: ${
                    formatoFecha(lectura.capturadaEn)
                }",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )
        }

        BotonPrincipal(
            texto = if (consultando) {
                "Actualizando..."
            } else {
                "↻  Actualizar lecturas"
            },
            cargando = consultando,
            onClick = {

                scope.launch {

                    consultando = true

                    mensajeSensores =
                        "Consultando Packet Tracer..."

                    try {

                        val nuevaLectura =
                            obtenerLecturaCompleta()

                        ultimaLectura = nuevaLectura

                        guardarLecturaLocal(
                            contexto,
                            uid,
                            nuevaLectura
                        )

                        mensajeSensores =
                            "Lecturas actualizadas correctamente."

                    } catch (e: Exception) {

                        mensajeSensores =
                            "No se pudieron actualizar las lecturas: ${e.localizedMessage}. Se conserva la última lectura."

                    } finally {

                        consultando = false
                    }
                }
            }
        )

        if (mensajeSensores.isNotEmpty()) {

            Text(
                text = mensajeSensores,
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )
        }

        // ==================================
        // FIREBASE Y SINCRONIZACION
        // ==================================

        TituloSeccion(
            titulo = "Almacenamiento",
            subtitulo = "Firebase Realtime Database"
        )

        TarjetaBase {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "☁️",
                    style =
                        MaterialTheme.typography.headlineMedium
                )

                Spacer(Modifier.width(12.dp))

                Column {

                    Text(
                        text = "Sincronización de datos",
                        fontWeight = FontWeight.Bold,
                        color = TextoPrincipal
                    )

                    Text(
                        text = "Guarda tus lecturas en la nube",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario
                    )
                }
            }

            Text(
                text = "Si no tienes internet, Firebase puede conservar la escritura pendiente y sincronizarla cuando vuelva la conexión.",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )

            BotonPrincipal(
                texto = "Guardar lectura en Firebase",
                habilitado = ultimaLectura != null,
                onClick = {

                    val lecturaParaGuardar = ultimaLectura

                    val usuarioActual = FirebaseAuth
                        .getInstance()
                        .currentUser

                    if (usuarioActual == null) {

                        mensajeGuardado =
                            "Debes iniciar sesión."

                    } else if (lecturaParaGuardar == null) {

                        mensajeGuardado =
                            "Primero actualiza las lecturas."

                    } else {

                        val datos = mapOf(
                            "temperatura" to
                                    lecturaParaGuardar.temperatura,

                            "humedad" to
                                    lecturaParaGuardar.humedad,

                            "nivelAgua" to
                                    lecturaParaGuardar.nivelAgua,

                            "capturadaEn" to
                                    lecturaParaGuardar.capturadaEn,

                            "fecha" to
                                    System.currentTimeMillis(),

                            "origen" to
                                    "Packet Tracer - ambiente MCU"
                        )

                        try {

                            val referencia = FirebaseDatabase
                                .getInstance()
                                .getReference("usuarios")
                                .child(usuarioActual.uid)
                                .child("lecturas")
                                .push()

                            mensajeGuardado =
                                "Lectura enviada a la cola local. Esperando confirmación..."

                            referencia.setValue(datos)
                                .addOnSuccessListener {

                                    mensajeGuardado =
                                        "Lectura sincronizada correctamente con Firebase."
                                }
                                .addOnFailureListener { error ->

                                    mensajeGuardado =
                                        "Error de Firebase: ${error.localizedMessage}"
                                }

                        } catch (e: Exception) {

                            mensajeGuardado =
                                "Error: ${e.localizedMessage}"
                        }
                    }
                }
            )

            if (mensajeGuardado.isNotEmpty()) {

                Text(
                    text = mensajeGuardado,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (
                        mensajeGuardado.contains("correctamente")
                    ) {
                        Verde
                    } else {
                        TextoSecundario
                    }
                )
            }
        }

        // ==================================
        // CONTROL DE DISPOSITIVOS
        // ==================================

        TituloSeccion(
            titulo = "Control de dispositivos",
            subtitulo = "Actuadores conectados al MCU"
        )

        TarjetaControl(
            titulo = "LED",
            icono = "💡",
            activo = ledActivo,
            verificado = ledVerificado,
            cargando = controlandoLed,
            mensaje = mensajeLed,
            onClick = {

                scope.launch {

                    controlandoLed = true

                    mensajeLed =
                        "Enviando comando..."

                    val encender = !ledActivo

                    val ruta = if (encender) {
                        "/led/on"
                    } else {
                        "/led/off"
                    }

                    val esperado = if (encender) {
                        "LED ENCENDIDO"
                    } else {
                        "LED APAGADO"
                    }

                    try {

                        val respuesta =
                            solicitarDispositivo(ruta)

                        if (respuesta == esperado) {

                            ledActivo = encender
                            ledVerificado = true
                            mensajeLed = respuesta

                        } else {

                            mensajeLed =
                                "Respuesta inesperada: $respuesta"
                        }

                    } catch (e: Exception) {

                        mensajeLed =
                            "Error LED: ${e.localizedMessage}"

                    } finally {

                        controlandoLed = false
                    }
                }
            }
        )

        TarjetaControl(
            titulo = "Alarma",
            icono = "🚨",
            activo = buzzerActivo,
            verificado = buzzerVerificado,
            cargando = controlandoBuzzer,
            mensaje = mensajeBuzzer,
            onClick = {

                scope.launch {

                    controlandoBuzzer = true

                    mensajeBuzzer =
                        "Enviando comando..."

                    val activar = !buzzerActivo

                    val ruta = if (activar) {
                        "/buzzer/on"
                    } else {
                        "/buzzer/off"
                    }

                    val esperado = if (activar) {
                        "ALARMA ACTIVADA"
                    } else {
                        "ALARMA DESACTIVADA"
                    }

                    try {

                        val respuesta =
                            solicitarDispositivo(ruta)

                        if (respuesta == esperado) {

                            buzzerActivo = activar
                            buzzerVerificado = true
                            mensajeBuzzer = respuesta

                        } else {

                            mensajeBuzzer =
                                "Respuesta inesperada: $respuesta"
                        }

                    } catch (e: Exception) {

                        mensajeBuzzer =
                            "Error alarma: ${e.localizedMessage}"

                    } finally {

                        controlandoBuzzer = false
                    }
                }
            }
        )

        // ==================================
        // CERRAR SESION
        // ==================================

        TarjetaBase {

            Text(
                text = "Mi cuenta",
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal
            )

            Text(
                text = usuario?.email ?: "Usuario autenticado",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )

            OutlinedButton(
                onClick = onCerrarSesion,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {

                Text(
                    text = "Cerrar sesión",
                    color = Rojo,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Estación Meteorológica IoT",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario
        )

        Spacer(Modifier.height(15.dp))
    }
}
