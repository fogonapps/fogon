package io.github.fogonapps.cajon.ui
import androidx.compose.runtime.Composable
import io.github.fogonapps.cajon.CajonViewModel
@Composable
fun SeguridadEquipo(vm: CajonViewModel, elevar: Boolean = false) { io.github.fogonapps.social.ui.SeguridadEquipo(interfazSocial(vm), elevar) }
@Composable
fun Equipo(vm: CajonViewModel) { io.github.fogonapps.social.ui.Equipo(interfazSocial(vm)) }
@Composable
fun RegistroEquipoPantalla(vm: CajonViewModel) { io.github.fogonapps.social.ui.RegistroEquipoPantalla(interfazSocial(vm)) }
