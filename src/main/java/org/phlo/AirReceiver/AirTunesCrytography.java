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

package org.phlo.AirReceiver;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.CipherSpi;

public final class AirTunesCrytography {
	/**
	 * Class is not meant to be instantiated
	 */
	private AirTunesCrytography() {
		throw new RuntimeException();
	}
	
	private static final Logger s_logger = Logger.getLogger(AirTunesCrytography.class.getName());

	/**
	 * The JCA/JCE Provider who supplies the necessary cryptographic algorithms
	 */
	private static final Provider Provider = new org.bouncycastle.jce.provider.BouncyCastleProvider();

	/**
	 * The AirTunes private key in PEM-encoded PKCS#8 format.
	 * Original Key from shairport was in PEM-encoded PKCS#1 format
	 */
	private static final String PrivateKeyData =
		"MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDn10TyouJ4i2wf\n" +
		"VaCOtwVEqPp5RaqL5sYs5fUcvdTcaEL+PRCD3S7ewb/UJS3ALm85i98OYUjqhIVe\n" +
		"LkQtptYmZPZ0ofMEkpreT2iT7y325xGox3oNkcnZgIIuUNEpIq/qQOqfDhTA92k4\n" +
		"xfOIL8AyPdn+VRVfUbtZIcIBYp/XM1LV4u+qv5ugSNe4E6K2dn9sPM8etM5nPQN7\n" +
		"DS6jDF//6wb40Ird5AlXGpxon+8QcohV3Yz7movvXIlD7ztfqhXd5pi+3fNZlgPr\n" +
		"Pm9hNyu2KPZVn1maeL9QBoeqf0l2wFYtQSlW+JieGKY1W9gVl4JeD8h1ND7HghF2\n" +
		"Jc2/mER7AgMBAAECggEBAOXwDHL1d9YEuaTOQSKqhLAXQ+yZWs/Mf0qyfAsYf5Bm\n" +
		"W+NZ3xJZgY3u7XnTse+EXk3d2smhVTc7XicNjhMVABouUn1UzfkACldovJjURGs3\n" +
		"u70Asp3YtTBiEzsqbnf07jJQViKQTacg+xwSwDmW2nE6BQYJjtvt7Pk20PqcvVkp\n" +
		"q7Dto1eZUC+YlNy4/FaaiS0XeAMkorbDFm40ZwkTS4VAQbhncGtY/vKg25Ird2KL\n" +
		"aOaWk8evQ78qc9C3Mjd6C6F7RPBR6b95hJ3LMzJXH9inCTPC1gvexHmTSj2spAu2\n" +
		"8vN8Cp0HEG6tyLNpoD8vQciACY6K3UYkDaxozFNU82ECgYEA9+C/Wh5nGDGai2IJ\n" +
		"wxcURARZ+XOFZhOxeuFQi7PmMW5rf0YtL31kQSuEt2vCPysMNWJFUnmyQ6n3MW+V\n" +
		"gAezTGH3aOLUTtX/KycoF+wys+STkpIo+ueOd0yg9169adWSAnmPEW42DGQ4sy4b\n" +
		"2LncHjIy8NMJGIg8xD743aIsNpECgYEA72//+ZTx5WRBqgA1/RmgyNbwI3jHBYDZ\n" +
		"xIQgeR30B8WR+26/yjIsMIbdkB/S+uGuu2St9rt5/4BRvr0M2CCriYdABgGnsv6T\n" +
		"kMrMmsq47Sv5HRhtj2lkPX7+D11W33V3otA16lQT/JjY8/kI2gWaN52kscw48V1W\n" +
		"CoPMMXFTyEsCgYEA0OuvvEAluoGMdXAjNDhOj2lvgE16oOd2TlB7t9Pf78fWeMZo\n" +
		"LT+tcTRBvurnJKCewJvcO8BwnJEz1Ins4qUa3QUxJ0kPkobRc8ikBU3CCldcfkwM\n" +
		"mDT0od6HSRej5ADq+IUGLbXLfjQ2iecR91/ng9fhkZL9dpzVQr6kuQEH7NECgYB/\n" +
		"QBjcfeopLaUwQjhvMQWgd4rcbz3mkNordMUFWYPt9XRmGi/Xt96AU8zA4gjwyKxi\n" +
		"b1l9PZnSzlGjezmuS36e8sB18L89g8rNMtqWkZLCiZI1glwH0c0yWaGQbNzUmcth\n" +
		"PiLJTLHqlxkGYJ3xsPSLBj8XNyA0NpSZtf35cO9EDQKBgQCQTukg+UTvWq98lCCg\n" +
		"D16bSAgsC4Tg+7XdoqImd9+3uEiNsr7mTJvdPKxm+jIOdvcc4q8icru9dsq5TghK\n" +
		"DEHZsHcdxjNAwazPWonaAbQ3mG8mnPDCFuFeoUoDjNppKvDrbbAOeIArkyUgTS0g\n" +
		"Aoo/jLE0aOgPZBiOEEa6G+RYpg==\n" +
		"";

	/**
	 * The AirTunes private key as an instance of {@link java.security.interfaces.RSAPrivateKey}
	 */
	public static final RSAPrivateKey PrivateKey = rsaPrivateKeyDecode(PrivateKeyData);

	static final Pattern s_transformation_pattern = Pattern.compile("^([A-Za-z0-9_.-]+)(/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+))?");
	/**
	 * Replacement for JCA/JCE's {@link javax.crypto.Cipher#getInstance}.
	 * The original method only accepts JCE providers from signed jars,
	 * which prevents us from bundling our cryptography provider Bouncy Caster
	 * with the application.
	 *
	 * @param transformation the transformation to find an implementation for
	 */
	public static Cipher getCipher(final String transformation) {
		try {
			/* Split the transformation into algorithm, mode and padding */

			final Matcher transformation_matcher = s_transformation_pattern.matcher(transformation.toUpperCase());
			if (!transformation_matcher.matches())
				throw new RuntimeException("Transformation " + transformation + " is invalid");

			final String algorithm = transformation_matcher.group(1);
			final String mode = transformation_matcher.group(3);
			final String padding = transformation_matcher.group(4);
			final boolean isBareAlgorithm = (mode == null) && (padding == null);

			/* Build the property values we need to search for. */

			final String algorithmModePadding = !isBareAlgorithm ? algorithm + "/" + mode + "/" + padding : null;
			final String algorithmMode = !isBareAlgorithm ? algorithm + "/" + mode : null;
			final String algorithmPadding = !isBareAlgorithm ? algorithm + "//" + padding : null;

			/* Search the provider for implementations. We ask for more specific (i.e matching
			 * the requested mode and or padding) implementation first, then fall back to more
			 * generals ones which we then must configure for the mode and padding.
			 */

			final CipherSpi cipherSpi;

			if (!isBareAlgorithm && (resolveProperty(Provider, "Cipher", algorithmModePadding) != null)) {
				@SuppressWarnings("unchecked")
				final
				Class<? extends CipherSpi> cipherSpiClass = (Class<? extends CipherSpi>)Class.forName(resolveProperty(Provider, "Cipher", algorithmModePadding));
				cipherSpi = cipherSpiClass.newInstance();
			}
			else if (!isBareAlgorithm && (resolveProperty(Provider, "Cipher", algorithmMode) != null)) {
				@SuppressWarnings("unchecked")
				final
				Class<? extends CipherSpi> cipherSpiClass = (Class<? extends CipherSpi>)Class.forName(resolveProperty(Provider, "Cipher", algorithmMode));
				cipherSpi = cipherSpiClass.newInstance();
				if (!isBareAlgorithm)
					cipherSpiSetPadding(cipherSpi, padding);
			}
			else if (!isBareAlgorithm && (resolveProperty(Provider, "Cipher", algorithmPadding) != null)) {
				@SuppressWarnings("unchecked")
				final
				Class<? extends CipherSpi> cipherSpiClass = (Class<? extends CipherSpi>)Class.forName(resolveProperty(Provider, "Cipher", algorithmPadding));
				cipherSpi = cipherSpiClass.newInstance();
				if (!isBareAlgorithm)
					cipherSpiSetMode(cipherSpi, mode);
			}
			else if (resolveProperty(Provider, "Cipher", algorithm) != null) {
				@SuppressWarnings("unchecked")
				final
				Class<? extends CipherSpi> cipherSpiClass = (Class<? extends CipherSpi>)Class.forName(resolveProperty(Provider, "Cipher", algorithm));
				cipherSpi = cipherSpiClass.newInstance();
				if (!isBareAlgorithm) {
					cipherSpiSetMode(cipherSpi, mode);
					cipherSpiSetPadding(cipherSpi, padding);
				}
			}
			else {
				throw new RuntimeException("Provider " + Provider.getName() + " (" + Provider.getClass() + ") does not implement " + transformation);
			}

			/* Create a {@link javax.crypto.Cipher} instance from the {@link javax.crypto.CipherSpi} the provider gave us */

			s_logger.info("Using SPI " + cipherSpi.getClass() + " for " + transformation);
			return getCipher(cipherSpi, transformation.toUpperCase());
		}
		catch (final RuntimeException e) {
			throw e;
		}
		catch (final Error e) {
			throw e;
		}
		catch (final Throwable e) {
			throw new RuntimeException("Provider " + Provider.getName() + " (" + Provider.getClass() + ") failed to instanciate " + transformation, e);
		}
	}

	/**
	 * Converts a PEM-encoded PKCS#8 private key into an RSAPrivateKey instance
	 * useable with JCE
	 *
	 * @param privateKey private key in PKCS#8 format and PEM-encoded
	 * @return RSAPrivateKey instance containing the key
	 */
	private static RSAPrivateKey rsaPrivateKeyDecode(final String privateKey) {
		try {
			final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			final KeySpec ks = new PKCS8EncodedKeySpec(Base64.decodePadded(privateKey));
			return (RSAPrivateKey)keyFactory.generatePrivate(ks);
		}
		catch (final Exception e) {
			throw new RuntimeException("Failed to decode built-in private key", e);
		}
	}

	/**
	 * Creates a {@link javax.crypto.Cipher} instance from a {@link javax.crypto.CipherSpi}.
	 *
	 * @param cipherSpi the {@link javax.cyrpto.CipherSpi} instance
	 * @param transformation the transformation cipherSpi was obtained for
	 * @return a {@link javax.crypto.Cipher} instance
	 * @throws Throwable in case of an error
	 */
	private static Cipher getCipher(final CipherSpi cipherSpi, final String transformation) throws Throwable {
		/* This API isn't public - usually you're expected to simply use Cipher.getInstance().
		 * Due to the signed-jar restriction for JCE providers that is not an option, so we
		 * use one of the private constructors of Cipher.
		 */
		final Class<Cipher> cipherClass = Cipher.class;
		final Constructor<Cipher> cipherConstructor = cipherClass.getDeclaredConstructor(CipherSpi.class, String.class);
		cipherConstructor.setAccessible(true);
		try {
			return cipherConstructor.newInstance(cipherSpi, transformation);
		}
		catch (final InvocationTargetException e) {
			throw e.getCause();
		}
	}

	/**
	 * Get the reflection of a private and protected method.
	 *
	 * Required since {@link java.lang.Class#getMethod(String, Class...)
	 * only find public method
	 *
	 * @param clazz the class to search in
	 * @param name the method name
	 * @param parameterTypes the method's parameter types
	 * @return the method reflection
	 * @throws NoSuchMethodException
	 */
	private static Method getMethod(final Class<?> clazz, final String name, final Class<?>... parameterTypes) throws NoSuchMethodException {
		try {
			/* Try to fetch the method reflection */
			return clazz.getDeclaredMethod(name, parameterTypes);
		}
		catch (final NoSuchMethodException e1) {
			/* No such method. Search base class if there is one,
			 * otherwise complain */
			if (clazz.getSuperclass() != null) {
				try {
					/* Try to fetch the method from the base class */
					return getMethod(clazz.getSuperclass(), name, parameterTypes);
				}
				catch (final NoSuchMethodException e2) {
					/* We don't want the exception to indicate that the
					 * *base* class didn't contain a matching method, but
					 * rather that the subclass didn't.
					 */
					final StringBuilder s = new StringBuilder();
					s.append(clazz.getName());
					s.append("(");
					boolean first = true;
					for(final Class<?> parameterType: parameterTypes) {
						if (!first)
							s.append(", ");
						first = false;
						s.append(parameterType);
					}
					s.append(")");

					throw new NoSuchMethodException(s.toString());
				}
			}
			else {
				/* No base class, complain */
				throw e1;
			}
		}
	}

	/**
	 * Sets the padding of a {@link javax.crypto.CipherSpi} instance.
	 *
	 * Like {@link #getCipher(String)}, we're accessing a private API
	 * here, so me must work around the access restrictions
	 *
	 * @param cipherSpi the {@link javax.crypto.CipherSpi} instance
	 * @param padding the padding to set
	 * @throws Throwable if {@link javax.crypto.CipherSpi#engineSetPadding} throws
	 */
	private static void cipherSpiSetPadding(final CipherSpi cipherSpi, final String padding) throws Throwable {
		final Method engineSetPadding = getMethod(cipherSpi.getClass(), "engineSetPadding", String.class);
		engineSetPadding.setAccessible(true);
		try {
			engineSetPadding.invoke(cipherSpi, padding);
		}
		catch (final InvocationTargetException e) {
			throw e.getCause();
		}
	}

	/**
	 * Sets the mode of a {@link javax.crypto.CipherSpi} instance.
	 *
	 * Like {@link #getCipher(String)}, we're accessing a private API
	 * here, so me must work around the access restrictions
	 *
	 * @param cipherSpi the {@link javax.crypto.CipherSpi} instance
	 * @param mode the mode to set
	 * @throws Throwable if {@link javax.crypto.CipherSpi#engineSetPadding} throws
	 */
	private static void cipherSpiSetMode(final CipherSpi cipherSpi, final String mode) throws Throwable {
		final Method engineSetMode = getMethod(cipherSpi.getClass(), "engineSetMode", String.class);
		engineSetMode.setAccessible(true);
		try {
			engineSetMode.invoke(cipherSpi, mode);
		}
		catch (final InvocationTargetException e) {
			throw e.getCause();
		}
	}

	/**
	 * Returns the value attached to a provider property.
	 *
	 * Supports aliases, i.e. if there is no property named
	 * type.name but one named Alg.Alias.type.name, the value
	 * of Alg.Alias.type.name is assumed to be the <b>name</b>
	 * of the actual property.
	 *
	 * @param provider JCE provider
	 * @param type type (Cipher, Algorithm, ...)
	 * @param name transformation
	 * @return the properties value which usually is the implementing class'es name
	 */
	private static String resolveProperty(final Provider provider, final String type, final String name) {
		if (Provider.getProperty(type + "." + name) != null)
			return Provider.getProperty(type + "." + name);
		else if (Provider.getProperty("Alg.Alias." + type + "." + name) != null)
			return resolveProperty(provider, type, Provider.getProperty("Alg.Alias." + type + "." + name));
		else
			return null;
	}
}
