/*
 * Copyright (c) 2015 Torsten Krause, Markenwerk GmbH.
 * 
 * This file is part of 'A DKIM library for JavaMail', hereafter
 * called 'this library', identified by the following coordinates:
 * 
 *    groupID: net.markenwerk
 *    artifactId: utils-mail-dkim
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 * 
 * See the LICENSE and NOTICE files in the root directory for further
 * information.
 * 
 * This file incorporates work covered by the following copyright and  
 * permission notice:
 *  
 *    Copyright 2008 The Apache Software Foundation or its licensors, as
 *    applicable.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *    A licence was granted to the ASF by Florian Sager on 30 November 2008
 */
package net.markenwerk.utils.mail.dkim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import com.sun.mail.smtp.SMTPMessage;

/**
 * Extension of SMTPMessage for the inclusion of a DKIM signature.
 * 
 * @author Torsten Krause (tk at markenwerk dot net)
 * @author Florian Sager
 * @since 1.0.0
 */
public class DkimMessage extends SMTPMessage {

	private static byte[] NL = { (byte) '\r', (byte) '\n' };

	private DkimSigner signer;

	private String encodedBody;

	/**
	 * Created a new {@code DkimMessage} from the given {@link MimeMessage} and
	 * {@link DkimSigner}.
	 * 
	 * @param message
	 *            The {@link MimeMessage} to be signed.
	 * @param signer
	 *            The {@link DkimSigner} to sign the message with.
	 * @throws MessagingException
	 *             If constructing this {@code DkimMessage} failed.
	 */
	public DkimMessage(MimeMessage message, DkimSigner signer) throws MessagingException {
		super(message);
		this.signer = signer;
	}

	/**
	 * Output the message as an RFC 822 format stream, without specified
	 * headers. If the <code>saved</code> flag is not set, the
	 * <code>saveChanges</code> method is called. If the <code>modified</code>
	 * flag is not set and the <code>content</code> array is not null, the
	 * <code>content</code> array is written directly, after writing the
	 * appropriate message headers.
	 *
	 * This method enhances the JavaMail method
	 * {@link MimeMessage#writeTo(OutputStream, String[])} See the according Sun
	 * license, this contribution is CDDL.
	 * 
	 * @exception MessagingException
	 * @exception IOException
	 *                if an error occurs writing to the stream or if an error is
	 *                generated by the javax.activation layer.
	 */
	@Override
	public void writeTo(OutputStream os, String[] ignoreList) throws IOException, MessagingException {

		ByteArrayOutputStream osBody = new ByteArrayOutputStream();

		// Inside saveChanges() it is assured that content encodings are set in
		// all parts of the body
		if (!saved) {
			saveChanges();
		}

		// First, write out the body to the body buffer
		if (modified) {
			// Finally, the content. Encode if required.
			// TODO: May need to account for ESMTP ?
			OutputStream osEncoding = MimeUtility.encode(osBody, this.getEncoding());
			this.getDataHandler().writeTo(osEncoding);
			osEncoding.flush(); // Needed to complete encoding
		} else {
			// Else, the content is untouched, so we can just output it
			// Finally, the content.
			if (content == null) {
				// call getContentStream to give subclass a chance to
				// provide the data on demand
				InputStream is = getContentStream();
				// now copy the data to the output stream
				byte[] buffer = new byte[8192];
				int length;
				while ((length = is.read(buffer)) > 0) {
					osBody.write(buffer, 0, length);
				}
				is.close();
			} else {
				osBody.write(content);
			}
			osBody.flush();
		}
		encodedBody = osBody.toString();

		// Second, sign the message
		String signatureHeaderLine = signer.sign(this);

		// write the 'DKIM-Signature' header, all other headers and a clear \r\n
		writeln(os, signatureHeaderLine);
		@SuppressWarnings("unchecked")
		Enumeration<String> headerLines = getNonMatchingHeaderLines(ignoreList);
		while (headerLines.hasMoreElements()) {
			writeln(os, headerLines.nextElement());
		}
		writeln(os);
		os.flush();

		// write the message body
		os.write(osBody.toByteArray());
		os.flush();
	}

	protected String getEncodedBody() {
		return encodedBody;
	}

	@Override
	public void setAllow8bitMIME(boolean allow) {
		// don't allow to switch to 8-bit MIME, instead 7-bit ASCII should be
		// kept because in forwarding scenarios a change to
		// Content-Transfer-Encoding to 7-bit ASCII breaks the DKIM signature
		super.setAllow8bitMIME(false);
	}

	private static void writeln(OutputStream out) throws IOException {
		out.write(NL);
	}

	private static void writeln(OutputStream out, String string) throws IOException {
		byte[] bytes = getBytes(string);
		out.write(bytes);
		out.write(NL);
	}

	private static byte[] getBytes(String string) {
		char[] chars = string.toCharArray();
		byte[] bytes = new byte[chars.length];

		for (int i = 0, n = chars.length; i < n; i++) {
			bytes[i] = (byte) chars[i];
		}

		return bytes;
	}

}
