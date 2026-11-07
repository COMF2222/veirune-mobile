package work.comf.fastcloud.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

public class PlaybackRulesTest {

  @Test
  public void seekingBufferingAndLongSuspensionDoNotCount() {
    assertFalse(PlaybackRules.heard(false, 1000, 1000, 1));
    assertFalse(PlaybackRules.heard(true, 1000, 30000, 1));
    assertFalse(PlaybackRules.heard(true, 10000, 10000, 1));
    assertTrue(PlaybackRules.heard(true, 1000, 1500, 1.5f));
  }

  @Test
  public void shortTracksLimitCrossfade() {
    assertEquals(8000, PlaybackRules.fadeDuration(8000, 180000, 200000));
    assertEquals(2000, PlaybackRules.fadeDuration(8000, 4000, 200000));
    assertEquals(0, PlaybackRules.fadeDuration(0, 180000, 200000));
  }

  @Test
  public void crossfadeHasEqualPowerAndExactEndpoints() {
    assertEquals(1, PlaybackRules.outgoingGain(0), .00001);
    assertEquals(0, PlaybackRules.incomingGain(0), .00001);
    assertEquals(0, PlaybackRules.outgoingGain(1), .00001);
    assertEquals(1, PlaybackRules.incomingGain(1), .00001);
    double a = PlaybackRules.outgoingGain(.5),
      b = PlaybackRules.incomingGain(.5);
    assertEquals(1, a * a + b * b, .00001);
  }

  @Test
  public void enteringAndLeavingCrossfadeNeverRemovesNormalizationAttenuation() {
    float before = PlaybackRules.volume(.25, 1);
    assertEquals(before, PlaybackRules.volume(.25, PlaybackRules.outgoingGain(0)), .00001);
    for (int step = 0; step <= 100; step++) {
      assertTrue(PlaybackRules.volume(.25, PlaybackRules.outgoingGain(step / 100.0)) <= before);
    }
    // The prepared next song remains silent, including after the effects tick.
    assertEquals(0, PlaybackRules.volume(2, 0), 0);
    assertEquals(0, PlaybackRules.volume(.4, PlaybackRules.incomingGain(0)), 0);
    // Switching the primary player keeps the incoming song's measured level.
    assertEquals(PlaybackRules.volume(.4, PlaybackRules.incomingGain(1)), PlaybackRules.volume(.4, 1), .00001);
    assertEquals(.25, PlaybackRules.volume(.25, 1), .00001); // cancellation
  }

  @Test
  public void invalidOrOutOfRangeMixEnvelopesCannotAmplifyThePlayer() {
    assertEquals(0, PlaybackRules.volume(1, Double.NaN), 0);
    assertEquals(0, PlaybackRules.volume(1, -1), 0);
    assertEquals(1, PlaybackRules.volume(2, 2), 0);
  }
}
