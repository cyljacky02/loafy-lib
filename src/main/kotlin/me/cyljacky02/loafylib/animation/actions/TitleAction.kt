package me.cyljacky02.loafylib.animation.actions

import me.cyljacky02.loafylib.animation.core.AnimationAction
import me.cyljacky02.loafylib.animation.core.AnimationContext
import me.cyljacky02.loafylib.util.mini
import net.kyori.adventure.text.Component

/**
 * Show a title and subtitle to the player.
 *
 * This is an instant action - the title is shown once when the action starts.
 * The title display duration is controlled by fadeIn/stay/fadeOut parameters.
 *
 * @property title The main title text (MiniMessage format)
 * @property subtitle The subtitle text (MiniMessage format)
 * @property fadeInTicks Fade in duration in ticks
 * @property stayTicks Stay duration in ticks
 * @property fadeOutTicks Fade out duration in ticks
 */
data class TitleAction(
    val title: String = "",
    val subtitle: String = "",
    val fadeInTicks: Int = 10,
    val stayTicks: Int = 70,
    val fadeOutTicks: Int = 20
) : AnimationAction {

    override suspend fun setup(context: AnimationContext) {
        val titleComponent = if (title.isNotEmpty()) title.mini() else Component.empty()
        val subtitleComponent = if (subtitle.isNotEmpty()) subtitle.mini() else Component.empty()

        context.provider.showTitle(
            player = context.player,
            title = titleComponent,
            subtitle = subtitleComponent,
            fadeInTicks = fadeInTicks,
            stayTicks = stayTicks,
            fadeOutTicks = fadeOutTicks
        )
    }
}

