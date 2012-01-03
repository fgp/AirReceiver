/*
 * This file is part of AirReceiver.
 *
 * AirReceiver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AirReceiver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AirReceiver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phlo.audio;

public class TestJavaSoundSink {
	public static void main(String[] args) throws Exception {
		final double sampleRate = 44100;
		final double frequencyLeft = 500;
		final double frequencyRight = frequencyLeft * 1.01;
		
		final SampleBuffer sine = new SampleBuffer(new SampleDimensions(2, (int)(sampleRate / 10)));
		
		JavaSoundSink sink = new JavaSoundSink(44100, 2, new SampleSource() {
			@Override
			public SampleBuffer getSampleBuffer(double timeStamp) {
				timeStamp -= 0.01;
				
				for(int i=0; i < sine.getDimensions().samples; ++i) {
					sine.setSample(0, i, (float)Math.sin(2.0 * Math.PI * frequencyLeft * (timeStamp + (double)i / sampleRate)));
					sine.setSample(1, i, (float)Math.sin(2.0 * Math.PI * frequencyRight * (timeStamp + (double)i / sampleRate)));
				}
				sine.setTimeStamp(timeStamp);
				return sine;
			}
		});

		sink.setStartTime(sink.getNextTime() + 1.0);
		Thread.sleep(Math.round(1.5e4));

		sink.close();
	}
}
