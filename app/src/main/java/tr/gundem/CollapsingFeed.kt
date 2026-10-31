package tr.gundem

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/** Consume exactly the pixels by which the header moves; the list gets the rest. */
internal fun headerOffset(current:Float,delta:Float,height:Float):Float =
    (current+delta).coerceIn(-height.coerceAtLeast(0f),0f)

@Composable internal fun CollapsingFeed(header:@Composable ()->Unit,content:@Composable ColumnScope.()->Unit) {
    var offset by rememberSaveable { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    val connection=remember { object:NestedScrollConnection {
        override fun onPreScroll(available:Offset,source:NestedScrollSource):Offset {
            val old=offset.coerceIn(-height,0f)
            offset=headerOffset(old,available.y,height)
            return Offset(0f,offset-old)
        }
        override suspend fun onPostFling(consumed:Velocity,available:Velocity):Velocity {
            val target=if(offset < -height/2) -height else 0f
            if(offset!=target) animate(offset,target,animationSpec=tween(220)) { value,_ -> offset=value }
            return Velocity.Zero
        }
    } }
    Column(Modifier.fillMaxSize().nestedScroll(connection)) {
        // Measure the full header independently of its visible height, then slide
        // it through a clipped viewport. No sticky LazyColumn item is resized.
        Layout(content=header,modifier=Modifier.clipToBounds()) { measurables,constraints ->
            val children=measurables.map { it.measure(constraints.copy(minHeight=0,maxHeight=Constraints.Infinity)) }
            val full=children.maxOfOrNull { it.height } ?: 0
            height=full.toFloat()
            val y=offset.coerceIn(-height,0f).roundToInt()
            layout(constraints.maxWidth,(full+y).coerceAtLeast(0)) { children.forEach { it.placeRelative(0,y) } }
        }
        content()
    }
}
