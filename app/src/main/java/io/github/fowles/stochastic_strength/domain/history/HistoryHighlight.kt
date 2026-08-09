package io.github.fowles.stochastic_strength.domain.history

import io.github.fowles.stochastic_strength.data.model.MuscleGroup
import io.github.fowles.stochastic_strength.data.model.WeightUnit
import io.github.fowles.stochastic_strength.domain.WeightFormatter
import io.github.fowles.stochastic_strength.domain.progression.ProgressionPoint
import kotlin.math.roundToInt
import kotlin.random.Random

enum class HighlightKind { LIFT, MUSCLE }

data class HighlightSeries(
    val subject: String,
    val muscle: MuscleGroup?,
    val points: List<ProgressionPoint>,
    val kind: HighlightKind,
    val exerciseId: Long? = null,
)

data class HighlightConfig(
    val monthWindowMs: Long = 30L * 24 * 3600 * 1000,
    val quarterWindowMs: Long = 90L * 24 * 3600 * 1000,
    val liftMinGainKg: Float = 2f,
    val muscleMinGainFraction: Float = 0.03f,
    val quipOnlyProbability: Float = 0.25f,
)

/** A gym non-sequitur: terse, never explains itself, ends with a turn toward the lifter. Muscle-keyed quips only attach to that muscle. */
data class Quip(val text: String, val muscle: MuscleGroup? = null)

private data class Window(val ms: Long, val label: String)
private data class Candidate(val text: String, val muscle: MuscleGroup?)

object HistoryHighlight {

    val QUIPS: List<Quip> = listOf(
        Quip("The bar does not care about your feelings. Add weight to it anyway."),
        Quip("The iron never lies, and today it says nice things."),
        Quip("Gravity filed a complaint. Ignore it."),
        Quip("You cannot flex a spreadsheet. Go lift something."),
        Quip("Two objects fall at the same rate. The bar has been trying to prove this to you all session."),
        Quip("There's an outlier in the data. It's you. Stay one."),
        Quip("A lifter at rest tends to scroll. A lifter in motion tends to gain."),
        Quip("For every rep, an equal and opposite gain."),
        Quip("You are not the center of the gym. Leg day is."),
        Quip("Progress sweeps equal areas in equal times. Some weeks just feel slower."),
        Quip("The weights that used to beat you are extinct."),
        Quip("You are the only animal that picks up heavy things for no reason at all. Beautiful."),
        Quip("Something in you keeps volunteering to survive next week's version of this."),
        Quip("Your grandchildren inherit exactly none of this. Enjoy it personally."),
        Quip("Assume a lifter. Assume a bar. Everything else follows."),
        Quip("The proof is three sets long and ends in QED."),
        Quip("Watching a set doesn't change it. Doing it does."),
        Quip("Entropy is undefeated, but you're still up on the week."),
        Quip("You don't find out who you are on the easy sets."),
        Quip("Leverage is physics taking your side for once."),
        Quip("You didn't get this from nothing. You traded for it."),
        Quip("The increments are too small to feel and too large to ignore."),
        Quip("Progress compounds, and it's paying the interest to you."),
        Quip("The sum of the squats equals the size of the quads."),
        Quip("The pump is a rumor your body is spreading about itself."),
        Quip("If gains are real, you win everything. If not, you're still jacked. Lift."),
        Quip("The wager is free: worst case, you wasted an hour being strong."),
        Quip("The expected value of showing up has never once been negative."),
        Quip("The first program ever written was four lines: sleep, eat, lift, repeat."),
        Quip("Your post-set glow just registered on somebody's instruments."),
        Quip("Strength is the one thing that cannot be gifted."),
        Quip("Superposition collapses the moment the bar leaves the rack."),
        Quip("Doubt the mirror, the scale, and the estimate, but never the last set."),
        Quip("No machine can tell in advance if the program halts. Neither can you. Keep going."),
        Quip("A mole of anything is unimaginable. Three sets of eight is not. Start there."),
        Quip("Some results arrive with no proof attached. This one has a training log."),
        Quip("We are all made of star stuff. Yours is denser now."),
        Quip("Small numbers, added relentlessly, become an unreasonable number."),
        Quip("The running total doesn't care how you felt about any single entry."),
        Quip("Nobody is counting. The count is happening anyway."),
        Quip("Build the shape you want to be carrying in forty years."),
        Quip("One session is a dot. A year is a line."),
        Quip("The number on the screen is a guess. The one on the bar isn't."),
        Quip("Trust the math. Verify with the bar."),
        Quip("There's an unknown in every session. Today you solved for it."),
        Quip("The first set is the volunteer trial. Everything after is replication."),
        Quip("The 301st use for a peanut: pre-workout."),
        Quip("Tissue reorganizes quietly for weeks. Then one day the weight just moves."),
        Quip("The scaffolding goes up long before anything looks different."),
        Quip("Nothing you did is lost. It's held somewhere you can't see yet."),
        Quip("Depth isn't optional. The bottom is where the trip starts."),
        Quip("One rep is an experiment. Three sets is a replication study."),
        Quip("Doubt everything, then test it under load."),
        Quip("Someone in the gym is hoping you will ask them to spot you."),
        Quip("The simplest explanation for your gains: you did the work."),
        Quip("The priors have been updated. You got stronger."),
        Quip("Levo, ergo cresco: you lift, therefore you gain."),
        Quip("The plates whisper among themselves. They fear you now."),
        Quip("Rest days are just gains loading. Trust the process."),
        Quip("Your muscles wrote this. We just typed it."),
        Quip("Consistency is a flex nobody can see. Until they can."),
        Quip("The gym owes you nothing and paid you anyway."),
        Quip("Statistically significant gains, p < 0.05, bro."),
        Quip("Somewhere a barbell is telling its friends about you."),
        Quip("Way to nail the vanity lifts!", muscle = MuscleGroup.BICEPS),
        Quip("Beach muscles, activated.", muscle = MuscleGroup.BICEPS),
        Quip("The gun show has extended its residency.", muscle = MuscleGroup.BICEPS),
        Quip("Two tickets validated.", muscle = MuscleGroup.BICEPS),
        Quip("Nobody skips this day. Respect.", muscle = MuscleGroup.QUADS),
        Quip("The squat rack remembers everything. Today it remembers greatness.", muscle = MuscleGroup.QUADS),
        Quip("Stairs are about to get personal.", muscle = MuscleGroup.QUADS),
        Quip("Pecs appeal, quantified.", muscle = MuscleGroup.CHEST),
        Quip("Your chest has been certified load-bearing.", muscle = MuscleGroup.CHEST),
        Quip("Holding up the sky is just a very long isometric.", muscle = MuscleGroup.BACK),
        Quip("Rows today, no rows to hoe tomorrow.", muscle = MuscleGroup.BACK),
        Quip("Boulder shoulders: geology in progress.", muscle = MuscleGroup.SHOULDERS),
        Quip("Doorframes fear you now.", muscle = MuscleGroup.SHOULDERS),
        Quip("Three heads are better than one.", muscle = MuscleGroup.TRICEPS),
        Quip("The posterior chain is undefeated.", muscle = MuscleGroup.HAMSTRINGS),
        Quip("Hinge like nobody's watching.", muscle = MuscleGroup.HAMSTRINGS),
        Quip("The engine room got an upgrade.", muscle = MuscleGroup.GLUTES),
        Quip("Powerhouse status: confirmed.", muscle = MuscleGroup.GLUTES),
        Quip("Calves of steel, patience of a saint.", muscle = MuscleGroup.CALVES),
        Quip("The most stubborn muscle finally blinked.", muscle = MuscleGroup.CALVES),
        Quip("Same hill, same rock, every day, forever. That's calf training.", muscle = MuscleGroup.CALVES),
        Quip("Abs are made in the kitchen but forged right here.", muscle = MuscleGroup.CORE),
        Quip("Your trunk is now load-bearing architecture.", muscle = MuscleGroup.CORE),
        Quip("The barbell is just a very honest friend."),
        Quip("Somewhere a dumbbell is name-dropping you at a party."),
        Quip("The safety pins are getting restless. Add more weight."),
        Quip("New plates get told about you on their first day."),
        Quip("A dumbbell is writing you into its memoir."),
        Quip("The kettlebells huddle closer when you walk past. They want to be picked."),
        Quip("Entropy scatters everything, except your gains."),
        Quip("Gravity requested a rematch tomorrow."),
        Quip("Somewhere a jar lid just felt a chill."),
        Quip("Your friends are planning a move. You're already on their short list."),
        Quip("Your PR has been notarized."),
        Quip("The Department of Gains has no further questions."),
        Quip("Form 1RM has been updated on your behalf."),
        Quip("The mitochondria is the powerhouse of the cell. You are the powerhouse of the gym."),
        Quip("Broscience says the pump lasts forever. Broscience is wrong, but we love it anyway."),
        Quip("Your future self already sent a thank-you note. Time travel is real if you lift."),
        Quip("Gains are a rumor you keep confirming."),
        Quip("The foam roller forgives all sins."),
        Quip("Discipline is just motivation with a mortgage."),
        Quip("The scale is a liar and the mirror is a poet. Trust the poet."),
        Quip("The hardest lift is the front door of the gym. You cleared it."),
        Quip("Rest is not a reward, it's a requirement."),
        Quip("Your grip strength is applying for its own zip code."),
        Quip("Every set is a care package addressed to future you."),
        Quip("Curls for the girls, curls for the boys, curls for the abstract concept of curls.", muscle = MuscleGroup.BICEPS),
        Quip("The sleeve tax is now unaffordable. Congratulations.", muscle = MuscleGroup.BICEPS),
        Quip("Tree trunks don't apologize for taking up space.", muscle = MuscleGroup.QUADS),
        Quip("Leg day: the appointment you can never reschedule.", muscle = MuscleGroup.QUADS),
        Quip("The bench remembers your name. It's frightened, but it remembers.", muscle = MuscleGroup.CHEST),
        Quip("Push day, no push-back.", muscle = MuscleGroup.CHEST),
        Quip("Lats so wide they qualify as a weather system.", muscle = MuscleGroup.BACK),
        Quip("Cannonball delts, incoming.", muscle = MuscleGroup.SHOULDERS),
        Quip("The overhead press: humbling everyone since gravity was invented.", muscle = MuscleGroup.SHOULDERS),
        Quip("The horseshoe deepens. Luck not required.", muscle = MuscleGroup.TRICEPS),
        Quip("Triceps: two-thirds of the arm, one hundred percent of the attitude.", muscle = MuscleGroup.TRICEPS),
        Quip("The hamstrings whisper only two words: good morning.", muscle = MuscleGroup.HAMSTRINGS),
        Quip("Posterior chain, meet posterior gains.", muscle = MuscleGroup.HAMSTRINGS),
        Quip("The strongest muscle in the body just got a raise.", muscle = MuscleGroup.GLUTES),
        Quip("Glute day is leg day's better-dressed cousin.", muscle = MuscleGroup.GLUTES),
        Quip("Calves grow on a geologic schedule. Today they moved.", muscle = MuscleGroup.CALVES),
        Quip("The core holds the empire together. Long live the empire.", muscle = MuscleGroup.CORE),
        Quip("Bracing: the most underrated flex in the building.", muscle = MuscleGroup.CORE),
        Quip("The chalk bucket considers you a regular now."),
        Quip("The clips just want to be part of something heavy."),
        Quip("The lifting straps drew straws over who gets to hold on."),
        Quip("The rack pins no longer brace for impact."),
        Quip("The dip belt speaks of you in hushed, respectful clanks."),
        Quip("The treadmills watch you walk past and wonder what they did wrong."),
        Quip("The dumbbells rearranged themselves by who wants to be picked first."),
        Quip("The bench refuses to be called furniture anymore."),
        Quip("Word of your last set reached the cardio section. They don't even know what a set is."),
        Quip("The dumbbells have a group chat. You're the pinned message."),
        Quip("To the plates, you aren't a person. You're a force of nature."),
        Quip("The lighter dumbbells relax when you walk past. They know they're safe."),
        Quip("Somewhere in the rack, a plate is bragging that it was there."),
        Quip("When you chalk up, the whole rack goes quiet."),
        Quip("The 2.5s wait all week to matter this much."),
        Quip("Every plate on the rack is doing its best to look heavy for you."),
        Quip("The treadmill dreams of being a rack when it grows up. You're why."),
        Quip("One of the dumbbells is writing a screenplay about you."),
        Quip("The Department of Gains has stamped your file: APPROVED."),
        Quip("Your gains have been entered into the permanent record. There is no appeals process."),
        Quip("The Bureau of Heavy Objects has upgraded your clearance."),
        Quip("Audit complete. Every rep accounted for. The Department thanks you."),
        Quip("Your progressive overload permit has been renewed."),
        Quip("The Ministry of Plates has approved your requisition for two more."),
        Quip("Gravity demanded arbitration. The barbell declined to testify."),
        Quip("Gravity has questions. You keep answering in reps."),
        Quip("Local gravity reports feeling 'taken for granted.'"),
        Quip("Gravity trains hard too, but it never takes a rest day. That's why it's losing."),
        Quip("Every jar lid in your zip code just signed a surrender treaty."),
        Quip("Pickle jars now open themselves out of respect."),
        Quip("Stuck bolts loosen preemptively now."),
        Quip("Kids and dogs now board your shoulders like public transit."),
        Quip("Neighbors time their furniture deliveries to your gym schedule."),
        Quip("You pushed a heavy door when it said pull. It opened anyway."),
        Quip("Passengers surreptitiously check you out when you put things in overhead bins."),
        Quip("The couch went up the stairs like it was nothing."),
        Quip("Coworkers chug water, just to watch you swap the jug out."),
        Quip("Escalator's broken. You didn't even notice."),
        Quip("The suitcase weight limit fears your definition of 'carry-on.'"),
        Quip("The bards are workshopping a saga about your training log."),
        Quip("The prophecy was vague on details but definitely wants more plates."),
        Quip("Legends say the plates still whisper your tempo."),
        Quip("In the old tongue, your rep scheme translates to 'unreasonable.'"),
        Quip("The oracle looked at your progression chart and simply nodded."),
        Quip("Reviewer 2 could find no flaws in your squat. Reviewer 2 is devastated."),
        Quip("Your gains passed peer review. Minor revisions: add weight."),
        Quip("Replication study complete: the gains reproduce."),
        Quip("Your training log has been cited twice this week."),
        Quip("Your methods section is one line: 'showed up.' It keeps replicating."),
        Quip("Broscience journal, volume 1: the pump is temporary, the legend is forever."),
        Quip("Half your warmup is someone else's max. Be gracious about it."),
        Quip("You've reached the level where 'light day' needs air quotes."),
        Quip("Somewhere a gym bro just used you as a motivational example."),
        Quip("Ninety seconds is all the bar gets to compose itself."),
        Quip("The plates use your rest to talk strategy."),
        Quip("The mirror started warming up. It wants to keep pace."),
        Quip("Muscle memory called. It says thanks for the material."),
        Quip("Your warm-up weight used to be your max. Nobody talks about it. Everybody noticed."),
        Quip("Soreness is just applause from the inside."),
        Quip("Tendons: quietly becoming suspension cables."),
        Quip("The curl bar asked for you by name.", muscle = MuscleGroup.BICEPS),
        Quip("Sleeves are a rental. Today's rent went up.", muscle = MuscleGroup.BICEPS),
        Quip("Your biceps have applied for landmark status.", muscle = MuscleGroup.BICEPS),
        Quip("The leg press just requested backup.", muscle = MuscleGroup.QUADS),
        Quip("Jeans are drafting a strongly worded letter.", muscle = MuscleGroup.QUADS),
        Quip("Your quads have opinions about elevators.", muscle = MuscleGroup.QUADS),
        Quip("The bar will descend. It will not stay.", muscle = MuscleGroup.CHEST),
        Quip("Armor, self-installed.", muscle = MuscleGroup.CHEST),
        Quip("The bench has seen things. It will see more.", muscle = MuscleGroup.CHEST),
        Quip("Pull like the floor owes you money.", muscle = MuscleGroup.BACK),
        Quip("The deadlift is just picking things up with commitment.", muscle = MuscleGroup.BACK),
        Quip("Wings under construction. Aviation authorities notified.", muscle = MuscleGroup.BACK),
        Quip("Overhead is a direction, not a limit.", muscle = MuscleGroup.SHOULDERS),
        Quip("Your silhouette is filing for more space.", muscle = MuscleGroup.SHOULDERS),
        Quip("Delts: nature's shoulder pads, now in production.", muscle = MuscleGroup.SHOULDERS),
        Quip("Push until the rope begs for mercy.", muscle = MuscleGroup.TRICEPS),
        Quip("The back of the arm is the front of the argument.", muscle = MuscleGroup.TRICEPS),
        Quip("Lockout is a lifestyle.", muscle = MuscleGroup.TRICEPS),
        Quip("The muscles you can't see in the mirror are the ones everyone else notices.", muscle = MuscleGroup.HAMSTRINGS),
        Quip("RDLs: bowing to no one, repeatedly.", muscle = MuscleGroup.HAMSTRINGS),
        Quip("Behind every great squat is a greater hamstring.", muscle = MuscleGroup.HAMSTRINGS),
        Quip("Hills are about to feel flat.", muscle = MuscleGroup.GLUTES),
        Quip("The pep in your step has infrastructure now.", muscle = MuscleGroup.GLUTES),
        Quip("Every sprint you'll ever run just got funded.", muscle = MuscleGroup.GLUTES),
        Quip("Bridges built, tolls collected.", muscle = MuscleGroup.GLUTES),
        Quip("Diamonds form under exactly this kind of pressure.", muscle = MuscleGroup.CALVES),
        Quip("Ankle scaffolding, reinforced.", muscle = MuscleGroup.CALVES),
        Quip("One more rep for the muscle that reads slow but compounds forever.", muscle = MuscleGroup.CALVES),
        Quip("The brace is silent, but everything hears it.", muscle = MuscleGroup.CORE),
        Quip("Six-pack pending; vault door installed.", muscle = MuscleGroup.CORE),
        Quip("Every heavy lift is secretly a core lift.", muscle = MuscleGroup.CORE),
    )

    private val genericQuips = QUIPS.filter { it.muscle == null }

    private val PLURAL_MUSCLES = setOf(
        MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.TRICEPS,
        MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES,
    )

    fun pick(
        series: List<HighlightSeries>,
        weightUnit: WeightUnit,
        nowMs: Long,
        random: Random,
        config: HighlightConfig = HighlightConfig(),
    ): String {
        val windows = listOf(
            Window(config.monthWindowMs, "this month"),
            Window(config.quarterWindowMs, "this quarter"),
        )
        val candidates = series.flatMap { s ->
            windows.mapNotNull { w -> candidate(s, w, weightUnit, nowMs, config) }
        }

        // Playful: sometimes (or always, when nothing qualifies) just show a standalone quip.
        if (candidates.isEmpty() || random.nextFloat() < config.quipOnlyProbability) {
            return genericQuips.random(random).text
        }

        // A stat always brings a quip along.
        val chosen = candidates.random(random)
        val eligible = QUIPS.filter { it.muscle == null || it.muscle == chosen.muscle }
        return "${chosen.text} ${eligible.random(random).text}"
    }

    /** Filter series down to lifts/muscles performed in one session. */
    fun scopeToSession(
        series: List<HighlightSeries>,
        exerciseIds: Set<Long>,
        muscles: Set<MuscleGroup>,
    ): List<HighlightSeries> = series.filter { s ->
        when (s.kind) {
            HighlightKind.LIFT -> s.exerciseId in exerciseIds
            HighlightKind.MUSCLE -> s.muscle in muscles
        }
    }

    private fun candidate(
        s: HighlightSeries,
        w: Window,
        unit: WeightUnit,
        nowMs: Long,
        config: HighlightConfig,
    ): Candidate? {
        val latest = s.points.lastOrNull { it.timestampMs <= nowMs } ?: return null
        val baseline = s.points.lastOrNull { it.timestampMs <= nowMs - w.ms } ?: return null
        val gain = latest.value - baseline.value
        return when (s.kind) {
            HighlightKind.LIFT -> {
                if (gain < config.liftMinGainKg) return null
                Candidate("Your ${s.subject} is up ${WeightFormatter.format(gain, unit)} ${w.label}.", s.muscle)
            }
            HighlightKind.MUSCLE -> {
                if (baseline.value <= 0f) return null
                val frac = gain / baseline.value
                if (frac < config.muscleMinGainFraction) return null
                val pct = (frac * 100f).roundToInt()
                val verb = if (s.muscle in PLURAL_MUSCLES) "are" else "is"
                Candidate("Your ${s.subject.lowercase()} $verb up $pct% ${w.label}.", s.muscle)
            }
        }
    }
}
