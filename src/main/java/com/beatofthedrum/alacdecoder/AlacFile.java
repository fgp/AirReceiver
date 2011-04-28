/*
** AlacFile.java
**
** Copyright (c) 2011 Peter McQuillan
**
** All Rights Reserved.
**                       
** Distributed under the BSD Software License (see license.txt)  
**
*/
package com.beatofthedrum.alacdecoder;

public class AlacFile
{

	byte input_buffer[];
	int ibIdx = 0;
	int input_buffer_bitaccumulator = 0; /* used so we can do arbitary
						bit reads */

	int samplesize = 0;
	int numchannels = 0;
	int bytespersample = 0;

    LeadingZeros lz = new LeadingZeros();


    private int buffer_size = 16384;
    /* buffers */
	int predicterror_buffer_a[] = new int[buffer_size];
	int predicterror_buffer_b[] = new int[buffer_size];

	int outputsamples_buffer_a[] = new int[buffer_size];
	int outputsamples_buffer_b[] = new int[buffer_size];

	int uncompressed_bytes_buffer_a[] = new int[buffer_size];
	int uncompressed_bytes_buffer_b[] = new int[buffer_size];



	/* stuff from setinfo */
	public int setinfo_max_samples_per_frame = 0; // 0x1000 = 4096
	/* max samples per frame? */
	
	public int setinfo_7a = 0; // 0x00
	public int setinfo_sample_size = 0; // 0x10
	public int setinfo_rice_historymult = 0; // 0x28
	public int setinfo_rice_initialhistory = 0; // 0x0a
	public int setinfo_rice_kmodifier = 0; // 0x0e
	public int setinfo_7f = 0; // 0x02
	public int setinfo_80 = 0; // 0x00ff
	public int setinfo_82 = 0; // 0x000020e7
	/* max sample size?? */
	public int setinfo_86 = 0; // 0x00069fe4
	/* bit rate (avarge)?? */
	public int setinfo_8a_rate = 0; // 0x0000ac44
	/* end setinfo stuff */

    public int[] predictor_coef_table = new int[1024];
    public int[] predictor_coef_table_a = new int[1024];
    public int[] predictor_coef_table_b = new int[1024];
}