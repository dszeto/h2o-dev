package water.fvec;

import water.*;

/** A compression scheme, over a chunk - a single array of bytes.  The *actual*
 *  vector header info is in the Vec struct - which contains info to find all
 *  the bytes of the distributed vector.  This struct is basically a 1-entry
 *  chunk cache of the total vector.  Subclasses of this abstract class
 *  implement (possibly empty) compression schemes.  */
public abstract class Chunk extends Iced implements Cloneable {
  public long _start = -1;    // Start element; filled after AutoBuffer.read
  public int _len;            // Number of elements in this chunk
  protected Chunk _chk2;      // Normally==null, changed if chunk is written to
  public Vec _vec;            // Owning Vec; filled after AutoBuffer.read
  byte[] _mem; // Short-cut to the embedded memory; WARNING: holds onto a large array
  public byte[] getBytes() { return _mem; }
  public final int len() { return _len; } // Number of elements in this chunk

  /** Load a long value.  Floating point values are silently rounded to an
    * integer.  Throws if the value is missing.
    * <p>
    * Loads from the 1-entry chunk cache, or misses-out.  This version uses
    * absolute element numbers, but must convert them to chunk-relative indices
    * - requiring a load from an aliasing local var, leading to lower quality
    * JIT'd code (similar issue to using iterator objects).
    * <p>
    * Slightly slower than 'at0' since it range checks within a chunk. */
  final long  at8( long i ) { 
    long x = i-_start;
    if( 0 <= x && x < _len ) return at80((int)x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+_len));
  }

  /** Load a double value.  Returns Double.NaN if value is missing.
   *  <p>
   * Loads from the 1-entry chunk cache, or misses-out.  This version uses
   * absolute element numbers, but must convert them to chunk-relative indices
   * - requiring a load from an aliasing local var, leading to lower quality
   * JIT'd code (similar issue to using iterator objects).
   * <p>
   * Slightly slower than 'at80' since it range checks within a chunk. */
  final double at( long i ) { 
    long x = i-_start;
    if( 0 <= x && x < _len ) return at0((int)x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+_len));
  }

  /** Fetch the missing-status the slow way. */
  final boolean isNA(long i) {
    long x = i-_start;
    if( 0 <= x && x < _len ) return isNA0((int)x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+_len));
  }


  /** The zero-based API.  Somewhere between 10% to 30% faster in a tight-loop
   *  over the data than the generic at() API.  Probably no gain on larger
   *  loops.  The row reference is zero-based on the chunk, and should
   *  range-check by the JIT as expected.  */
  public final double  at0  ( int i ) { return _chk2 == null ? atd_impl(i) : _chk2. atd_impl(i); }
  public final long    at80 ( int i ) { return _chk2 == null ? at8_impl(i) : _chk2. at8_impl(i); }
  public final boolean isNA0( int i ) { return _chk2 == null ?isNA_impl(i) : _chk2.isNA_impl(i); }


  /** Write element the slow way, as a long.  There is no way to write a
   *  missing value with this call.  Under rare circumstances this can throw:
   *  if the long does not fit in a double (value is larger magnitude than
   *  2^52), AND float values are stored in Vector.  In this case, there is no
   *  common compatible data representation. */
  final long   set( long i, long   l) { long x = i-_start; return (0 <= x && x < _len) ? set0((int)x,l) : _vec.set(i,l); }
  /** Write element the slow way, as a double.  Double.NaN will be treated as
   *  a set of a missing element. */
  final double set( long i, double d) { long x = i-_start; return (0 <= x && x < _len) ? set0((int)x,d) : _vec.set(i,d); }
  /** Write element the slow way, as a float.  Float.NaN will be treated as
   *  a set of a missing element. */
  final float  set( long i, float  f) { long x = i-_start; return (0 <= x && x < _len) ? set0((int)x,f) : _vec.set(i,f); }
  /** Set the element as missing the slow way.  */
  final boolean setNA( long i )       { long x = i-_start; return (0 <= x && x < _len) ? setNA0((int)x) : _vec.setNA(i); }
  
  private void setWrite() {
    if( _chk2 != null ) return; // Already setWrite
    assert !(this instanceof NewChunk) : "Cannot direct-write into a NewChunk, only append";
    _vec.preWriting();          // One-shot writing-init
    _chk2 = (Chunk)clone();     // Flag this chunk as having been written into
    assert _chk2._chk2 == null; // Clone has NOT been written into
  }

  /**
   * Set a long element in a chunk given a 0-based chunk local index.
   *
   * Write into a chunk.
   * May rewrite/replace chunks if the chunk needs to be
   * "inflated" to hold larger values.  Returns the input value.
   *
   * Note that the idx is an int (instead of a long), which tells you
   * that index 0 is the first row in the chunk, not the whole Vec.
   */
  public final long set0(int idx, long l) {
    setWrite();
    if( _chk2.set_impl(idx,l) ) return l;
    (_chk2 = inflate_impl(new NewChunk(this))).set_impl(idx,l);
    return l;
  }

  /** Set a double element in a chunk given a 0-based chunk local index. */
  public final double set0(int idx, double d) {
    setWrite();
    if( _chk2.set_impl(idx,d) ) return d;
    (_chk2 = inflate_impl(new NewChunk(this))).set_impl(idx,d);
    return d;
  }

  /** Set a floating element in a chunk given a 0-based chunk local index. */
  public final float set0(int idx, float f) {
    setWrite();
    if( _chk2.set_impl(idx,f) ) return f;
    (_chk2 = inflate_impl(new NewChunk(this))).set_impl(idx,f);
    return f;
  }

  /** Set the element in a chunk as missing given a 0-based chunk local index. */
  public final boolean setNA0(int idx) {
    setWrite();
    if( _chk2.setNA_impl(idx) ) return true;
    (_chk2 = inflate_impl(new NewChunk(this))).setNA_impl(idx);
    return true;
  }

  /** After writing we must call close() to register the bulk changes */
  public void close( int cidx, Futures fs ) {
    if( this  instanceof NewChunk ) _chk2 = this;
    if( _chk2 == null ) return;          // No change?
    if( _chk2 instanceof NewChunk ) _chk2 = ((NewChunk)_chk2).new_close();
    DKV.put(_vec.chunkKey(cidx),_chk2,fs,true); // Write updated chunk back into K/V
    if( _vec._cache == this ) _vec._cache = null;
  }

  public int cidx() { return _vec.elem2ChunkIdx(_start); }

  /** Chunk-specific readers.  */ 
  abstract protected double   atd_impl(int idx);
  abstract protected long     at8_impl(int idx);
  abstract protected boolean isNA_impl(int idx);
  
  /** Chunk-specific writer.  Returns false if the value does not fit in the
   *  current compression scheme.  */
  abstract boolean set_impl  (int idx, long l );
  abstract boolean set_impl  (int idx, double d );
  abstract boolean set_impl  (int idx, float f );
  abstract boolean setNA_impl(int idx);

  int nextNZ(int rid){return rid+1;}
  protected boolean isSparse() {return false;}
  protected int sparseLen(){return _len;}

  /** Get chunk-relative indices of values (nonzeros for sparse, all for dense) stored in this chunk.
   *  For dense chunks, this will contain indices of all the rows in this chunk.
   *  @return array of chunk-relative indices of values stored in this chunk.
   */
  public int nonzeros(int [] res) {
    for( int i = 0; i < _len; ++i) res[i] = i;
    return _len;
  }

  /**
   * Get chunk-relative indices of values (nonzeros for sparse, all for dense) stored in this chunk.
   * For dense chunks, this will contain indices of all the rows in this chunk.
   *
   * @return array of chunk-relative indices of values stored in this chunk.
   */
  public final int [] nonzeros () {
    int [] res = MemoryManager.malloc4(sparseLen());
    nonzeros(res);
    return res;
  }

/** Chunk-specific bulk inflater back to NewChunk.  Used when writing into a
   *  chunk and written value is out-of-range for an update-in-place operation.
   *  Bulk copy from the compressed form into the nc._ls array.   */ 
  abstract NewChunk inflate_impl(NewChunk nc);

  /** Return the next Chunk, or null if at end.  Mostly useful for parsers or
   *  optimized stencil calculations that want to "roll off the end" of a
   *  Chunk, but in a highly optimized way. */
  Chunk nextChunk( ) { return _vec.nextChunk(this); }

  @Override public String toString() { return getClass().getSimpleName(); }

  long byteSize() {
    long s= _mem == null ? 0 : _mem.length;
    s += (2+5)*8 + 12; // 2 hdr words, 5 other words, @8bytes each, plus mem array hdr
    if( _chk2 != null ) s += _chk2.byteSize();
    return s;
  }

  // Custom serializers: the _mem field contains ALL the fields already.
  // Init _start to -1, so we know we have not filled in other fields.
  // Leave _vec & _chk2 null, leave _len unknown.
  abstract public AutoBuffer write_impl( AutoBuffer ab );
  abstract public Chunk read_impl( AutoBuffer ab );

  // -----------------
  // Support for fixed-width format printing
  private String pformat () { return pformat0(); }
  private int pformat_len() { return pformat_len0(); }
  protected String pformat0() { 
    long min = (long)_vec.min();
    if( min < 0 ) return "% "+pformat_len0()+"d";
    return "%"+pformat_len0()+"d";
  }
  protected int pformat_len0() { 
    int len=0;
    long min = (long)_vec.min();
    if( min < 0 ) len++;
    long max = Math.max(Math.abs(min),Math.abs((long)_vec.max()));
    throw H2O.unimpl();
    //for( int i=1; i<DParseTask.powers10i.length; i++ )
    //  if( max < DParseTask.powers10i[i] )
    //    return i+len;
    //return 20;
  }
  protected int pformat_len0( double scale, int lg ) {
    double dx = Math.log10(scale);
    int x = (int)dx;
    throw H2O.unimpl();
    //if( DParseTask.pow10i(x) != scale ) throw H2O.unimpl();
    //int w=1/*blank/sign*/+lg/*compression limits digits*/+1/*dot*/+1/*e*/+1/*neg exp*/+2/*digits of exp*/;
    //return w;
  }
}
