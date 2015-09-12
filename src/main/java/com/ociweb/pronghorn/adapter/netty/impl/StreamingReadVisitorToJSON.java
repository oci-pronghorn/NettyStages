package com.ociweb.pronghorn.adapter.netty.impl;

import java.nio.ByteBuffer;

import com.ociweb.pronghorn.pipe.stream.StreamingReadVisitor;

public class StreamingReadVisitorToJSON implements StreamingReadVisitor {

	StringBuilder workspace;
	
//	ByteBuffer tempByteBuffer = ByteBuffer.allocate(512);
	
	public StreamingReadVisitorToJSON(int biggestJSON) {
		this.workspace =  new StringBuilder(biggestJSON); 		 
	}
	
	@Override
	public boolean paused() {
		return false; //not used in this case because we block on out
	}
	
	@Override
	public void visitTemplateOpen(String name, long id) {
	    workspace.append("{\"").append(name).append("\":");
	}
	
	@Override
	public void visitTemplateClose(String name, long id) {
	    workspace.append("}");
	}

	@Override
	public void visitFragmentOpen(String name, long id, int cursor) {
	    workspace.append("{\"").append(name).append("\":");
	}

	@Override
	public void visitFragmentClose(String name, long id) {
	    workspace.append("}");		
	}

	@Override
	public void visitSequenceOpen(String name, long id, int length) {
	    workspace.append("[");	
	}

	@Override
	public void visitSequenceClose(String name, long id) {
	    workspace.append("]");
	}

	@Override
	public void visitSignedInteger(String name, long id, int value) {
	    workspace.append("{\"").append(name).append("\":").append(value).append("}");
	}

	@Override
	public void visitUnsignedInteger(String name, long id, long value) {
	    workspace.append("{\"").append(name).append("\":").append(value).append("}");
	}

	@Override
	public void visitSignedLong(String name, long id, long value) {
	    workspace.append("{\"").append(name).append("\":").append(value).append("}");
	}

	@Override
	public void visitUnsignedLong(String name, long id, long value) {
	    workspace.append("{\"").append(name).append("\":").append(value).append("}"); //TODO: this is not strictly right and can be negative!!
	}

	@Override
	public void visitDecimal(String name, long id, int exp, long mant) {
	    workspace.append("{\"").append(name).append("\":[").append(exp).append(",").append(mant).append("]}");
	}

	@Override
	public Appendable targetASCII(String name, long id) {
	    //add the prefix before the data is append	    
	    return workspace.append("{\"").append(name).append("\":\"");
	}

	@Override
	public void visitASCII(String name, long id, Appendable value) {
	    assert(value==workspace);
	    //add the suffix now that the data has been appended
	    workspace.append("\"}");
	}

	@Override
	public Appendable targetUTF8(String name, long id) {
        //add the prefix before the data is append      
        return workspace.append("{\"").append(name).append("\":\"");
	}

	@Override
	public void visitUTF8(String name, long id, Appendable value) {
        assert(value==workspace);
        //add the suffix now that the data has been appended
        workspace.append("\"}");
	}

	@Override
	public ByteBuffer targetBytes(String name, long id, int length) {
	    throw new UnsupportedOperationException("Unused in current demo"); //revisit and implement with CData type block encoding as base64
	}

	@Override
	public void visitBytes(String name, long id, ByteBuffer value) {
		//undefined how we should send a binary block to JSON
	}

	@Override
	public void startup() {
	}

	@Override
	public void shutdown() {
	}

}
