package org.phlo.AirReceiver;

import java.util.Arrays;
import java.util.logging.*;

import javax.sound.sampled.AudioFormat;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

import com.beatofthedrum.alacdecoder.*;

public class RaopRtpAudioAlacDecodeHandler extends OneToOneDecoder implements AudioFormatProvider {
	private static Logger s_logger = Logger.getLogger(RaopRtpAudioAlacDecodeHandler.class.getName());

	public static final int FormatOptionSamplesPerFrame = 0;
	public static final int FormatOption7a = 1;
	public static final int FormatOptionBitsPerSample = 2;
	public static final int FormatOptionRiceHistoryMult = 3;
	public static final int FormatOptionRiceInitialHistory = 4;
	public static final int FormatOptionRiceKModifier = 5;
	public static final int FormatOption7f = 6;
	public static final int FormatOption80 = 7;
	public static final int FormatOption82 = 8;
	public static final int FormatOption86 = 9;
	public static final int FormatOption8a_rate = 10;
	
	private static final AudioFormat AudioOutputFormat = new AudioFormat(
		44100 /* sample rate */,
		16 /* bits per sample */,
		2 /* number of channels */,
		false /* unsigned */,
		true /* big endian */
	);
	
	/**
	 * Number of samples per ALAC frame (packet).
	 * One sample here means *two* amplitues, one
	 * for the left channel and one for the right
	 */
	private final int m_samplesPerFrame;
	private final AlacFile m_alacFile;
	
	public RaopRtpAudioAlacDecodeHandler(String[] formatOptions)
		throws ProtocolException
	{
		m_samplesPerFrame = Integer.valueOf(formatOptions[FormatOptionSamplesPerFrame]);
		
		int bitsPerSample = Integer.valueOf(formatOptions[FormatOptionBitsPerSample]);
		if (bitsPerSample != 16)
			throw new ProtocolException("Sample size must be 16, but was " + bitsPerSample);
		
		int sampleRate = Integer.valueOf(formatOptions[FormatOption8a_rate]);
		if (sampleRate != 44100)
			throw new ProtocolException("Sample rate must be 44100, but was " + sampleRate);
		
		m_alacFile = AlacDecodeUtils.create_alac(bitsPerSample, 2);
		m_alacFile.setinfo_max_samples_per_frame = m_samplesPerFrame;
		m_alacFile.setinfo_7a = Integer.valueOf(formatOptions[FormatOption7a]);
		m_alacFile.setinfo_sample_size = bitsPerSample;
		m_alacFile.setinfo_rice_historymult = Integer.valueOf(formatOptions[FormatOptionRiceHistoryMult]);
		m_alacFile.setinfo_rice_initialhistory = Integer.valueOf(formatOptions[FormatOptionRiceInitialHistory]);
		m_alacFile.setinfo_rice_kmodifier = Integer.valueOf(formatOptions[FormatOptionRiceKModifier]);
		m_alacFile.setinfo_7f = Integer.valueOf(formatOptions[FormatOption7f]);
		m_alacFile.setinfo_80 = Integer.valueOf(formatOptions[FormatOption80]);
		m_alacFile.setinfo_82 = Integer.valueOf(formatOptions[FormatOption82]);
		m_alacFile.setinfo_86 = Integer.valueOf(formatOptions[FormatOption86]);
		m_alacFile.setinfo_8a_rate = sampleRate;
		
		s_logger.info("Created ALAC decode for options " + Arrays.toString(formatOptions));
	}
	
	@Override
	protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
		throws Exception
	{
		RaopRtpPacket.Audio alacPacket = (RaopRtpPacket.Audio)msg;
		
		/* The ALAC decode sometimes reads beyond the input's bounds
		 * (but later discards the data). To alleviate, we allocate
		 * 3 spare bytes at input buffer's end.
		 */
		final byte[] alacBytes = new byte[alacPacket.getPayload().capacity() + 3];
		alacPacket.getPayload().getBytes(0, alacBytes, 0, alacPacket.getPayload().capacity());

		/* Decode ALAC to PCM */
		final int[] pcmSamples = new int[m_samplesPerFrame * 2];
		final int pcmSamplesBytes = AlacDecodeUtils.decode_frame(m_alacFile, alacBytes, pcmSamples, m_samplesPerFrame);
		
		/* decode_frame() returns the number of *bytes*, not samples! */
		final int pcmSamplesLength = pcmSamplesBytes / 4;
		Level level = Level.FINEST;
		if (s_logger.isLoggable(level))
			s_logger.log(level, "Decoded " + alacBytes.length + " bytes of ALAC audio data to " + pcmSamplesLength + " PCM samples");
		
		/* Complain if the sender doesn't honour it's commitment */
		if (pcmSamplesLength != m_samplesPerFrame)
			throw new ProtocolException("Frame declared to contain " + m_samplesPerFrame + ", but contained " + pcmSamplesLength);

		/* Assemble PCM audio packet from original packet header and decoded data.
		 * The ALAC decode emits signed PCM samples, so we must convert them to unsigned
		 * PCM before stuffing them into the packet
		 */
		RaopRtpPacket.Audio pcmPacket;
		if (alacPacket instanceof RaopRtpPacket.AudioTransmit) {
			pcmPacket = new RaopRtpPacket.AudioTransmit(pcmSamplesLength * 4);
			alacPacket.getBuffer().getBytes(0, pcmPacket.getBuffer(), 0, RaopRtpPacket.AudioTransmit.Length);
		}
		else if (alacPacket instanceof RaopRtpPacket.AudioRetransmit) {
			pcmPacket = new RaopRtpPacket.AudioRetransmit(pcmSamplesLength * 4);
			alacPacket.getBuffer().getBytes(0, pcmPacket.getBuffer(), 0, RaopRtpPacket.AudioRetransmit.Length);
		}
		else
			throw new ProtocolException("Packet type " + alacPacket.getClass() + " is not supported by the ALAC decoder");
		for(int i=0; i < pcmSamples.length; ++i) {
			/* Convert sample to unsigned PCM */
			int pcmSampleUnsigned = pcmSamples[i] + 0x8000;
			
			pcmPacket.getPayload().setByte(2*i, (pcmSampleUnsigned & 0xff00) >> 8);
			pcmPacket.getPayload().setByte(2*i + 1, pcmSampleUnsigned & 0x00ff);
		}
		
		return pcmPacket;
	}

	@Override
	public AudioFormat getAudioFormat() {
		return AudioOutputFormat;
	}

	@Override
	public int getFramesPerPacket() {
		return m_samplesPerFrame;
	}
}
