
'use strict';


  var encodeSingleChar = function(c, buffer, mask, pos) {

	    if (c <= 0x007F) { // less than or equal to 7 bits or 127
	        // code point 7
	        buffer[mask&pos++] = (byte) c;
	    } else {
	        if (c <= 0x07FF) { // less than or equal to 11 bits or 2047
	            // code point 11
	            buffer[mask&pos++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
	        } else {
	            if (c <= 0xFFFF) { // less than or equal to  16 bits or 65535

	            	//special case logic here because we know that c > 7FF and c <= FFFF so it may hit these
	            	// D800 through DFFF are reserved for UTF-16 and must be encoded as an 63 (error)
	            	if (0xD800 == (0xF800&c)) {
	            		buffer[mask&pos++] = 63;
	            		return pos;
	            	}

	                // code point 16
	                buffer[mask&pos++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
	            } else {
	                int pos1 = pos;
                    if (c < 0x1FFFFF) {
                        // code point 21
                        buffer[mask&pos1++] = (byte) (0xF0 | ((c >> 18) & 0x07));
                    } else {
                        if (c < 0x3FFFFFF) {
                            // code point 26
                            buffer[mask&pos1++] = (byte) (0xF8 | ((c >> 24) & 0x03));
                        } else {
                            if (c < 0x7FFFFFFF) {
                                // code point 31
                                buffer[mask&pos1++] = (byte) (0xFC | ((c >> 30) & 0x01));
                            } else {
                                throw new UnsupportedOperationException("can not encode char with value: " + c);
                            }
                            buffer[mask&pos1++] = (byte) (0x80 | ((c >> 24) & 0x3F));
                        }
                        buffer[mask&pos1++] = (byte) (0x80 | ((c >> 18) & 0x3F));
                    }
                    buffer[mask&pos1++] = (byte) (0x80 | ((c >> 12) & 0x3F));
                    pos = pos1;
	            }
	            buffer[mask&pos++] = (byte) (0x80 | ((c >> 6) & 0x3F));
	        }
	        buffer[mask&pos++] = (byte) (0x80 | (c & 0x3F));
	    }
	    return pos;
	}
	
	
	var decodeSingleChar = function(source, posAndChar, mask) { 

		  // 7  //high bit zero all others its 1
		  // 5 6
		  // 4 6 6
		  // 3 6 6 6
		  // 2 6 6 6 6
		  // 1 6 6 6 6 6

	    var sourcePos = 0xFFFF & (posAndChar >> 32);

	    var b;
	    if ((b = source[mask&sourcePos++]) >= 0) {
	        // code point 7
	        return ((sourcePos)<<32) | b; //1 byte result of 7 bits with high zero
	    }

	    var result;
	    if (((byte) (0xFF & (b << 2))) >= 0) {
	        if ((b & 0x40) == 0) {
	            ++sourcePos;
	            return ((sourcePos)<<32) | 0xFFFD; // Bad data replacement char
	        }
	        // code point 11
	        result = (b & 0x1F); //5 bits
	    } else {
	        if (( (0xFF & (b << 3))) >= 0) {
	            // code point 16
	            result = (b & 0x0F); //4 bits
	        } else {
	            if (( (0xFF & (b << 4))) >= 0) {
	                // code point 21
	                result = (b & 0x07); //3 bits
	            } else {
	                if (( (0xFF & (b << 5))) >= 0) {
	                    // code point 26
	                    result = (b & 0x03); // 2 bits
	                } else {
	                    if (( (0xFF & (b << 6))) >= 0) {
	                        // code point 31
	                        result = (b & 0x01); // 1 bit
	                    } else {
	                        // the high bit should never be set
	                        sourcePos += 5;
	                        return ((sourcePos)<<32) | 0xFFFD; // Bad data replacement char
	                    }

	                    if ((source[mask&sourcePos] & 0xC0) != 0x80) {
	                        sourcePos += 5;
	                        return ((sourcePos)<<32) | 0xFFFD; // Bad data replacement char
	                    }
	                    result = (result << 6) | (int)(source[mask&sourcePos++] & 0x3F);
	                }
	                if ((source[mask&sourcePos] & 0xC0) != 0x80) {
	                    sourcePos += 4;
	                    return ((sourcePos)<<32) | 0xFFFD; // Bad data replacement char
	                }
	                result = (result << 6) | (int)(source[mask&sourcePos++] & 0x3F);
	            }
	            if ((source[mask&sourcePos] & 0xC0) != 0x80) {
	                sourcePos += 3;
	                return ((sourcePos)<<32) | 0xFFFD; // Bad data replacement char
	            }
	            result = (result << 6) | (int)(source[mask&sourcePos++] & 0x3F);
	        }
	        if ((source[mask&sourcePos] & 0xC0) != 0x80) {
	            sourcePos += 2;
	            return ((sourcePos)<<32) | 0xFFFD; // Bad data replacement char
	        }
	        result = (result << 6) | (int)(source[mask&sourcePos++] & 0x3F);
	    }
	    if ((source[mask&sourcePos] & 0xC0) != 0x80) {
	       System.err.println("Invalid encoding, low byte must have bits of 10xxxxxx but we find "+Integer.toBinaryString(source[mask&sourcePos]));
	       sourcePos += 1;
	       return ((sourcePos)<<32) | 0xFFFD; // Bad data replacement char
	    }
	    var chr = ((result << 6) | (source[mask&sourcePos++] & 0x3F)); //6 bits
	    return ((sourcePos)<<32) | chr;
	  }
	 	  
	  var decodeUTF8 = function(source, bytesLen, bytesPos) {
	     var result = '';
	     var charAndPos = bytesPos<<32;
	     var limit = (bytesPos+bytesLen)<<32;
	     while (charAndPos<limit) {
	     	charAndPos = decodeSingleChar(source, charAndPos, 0xFFFFFFFF);
	     	result = result + (0xFFFFFFFF&charAndPos);
	     }
	     return result;	   
	  }
	