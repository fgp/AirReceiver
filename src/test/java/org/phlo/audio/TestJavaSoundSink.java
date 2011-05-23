package org.phlo.audio;

public class TestJavaSoundSink {
	public static void main(String[] args) throws Exception {
		final float sampleRate = 44100;
		final double frequencyLeft = 500;
		final double frequencyRight = frequencyLeft * 2.0;
		
		final SampleBuffer sine = new SampleBuffer(new SampleDimensions(2, (int)(sampleRate / 10)));
		
		final float[][] sineSamples = sine.getSamples();
		for(int i=0; i < sine.getDims().samples; ++i) {
			sineSamples[0][i] = (float)Math.sin(2.0 * Math.PI * i * frequencyLeft / sampleRate);
			sineSamples[1][i] = (float)Math.sin(2.0 * Math.PI * i * frequencyRight / sampleRate);
		}
		
		JavaSoundSink sink = new JavaSoundSink(44100, 2, new SampleSource() {
			@Override
			public SampleBuffer getSampleBuffer(double timeStamp) {
				sine.setTimeStamp(timeStamp - 1.0 / (frequencyLeft));
				return sine;
			}
		});

		sink.setStartTime(sink.getNextTime() + 1.0);
		Thread.sleep(Math.round(1.5e3));

		sink.close();
	}
}
