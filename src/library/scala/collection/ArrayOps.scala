package scala
package collection

import mutable.ArrayBuilder
import immutable.Range
import scala.reflect.ClassTag
import scala.math.{max, min, Ordering}
import scala.Predef.{ // unimport all array-related implicit conversions to avoid triggering them accidentally
  genericArrayOps => _,
  booleanArrayOps => _,
  byteArrayOps => _,
  charArrayOps => _,
  doubleArrayOps => _,
  floatArrayOps => _,
  intArrayOps => _,
  longArrayOps => _,
  refArrayOps => _,
  shortArrayOps => _,
  unitArrayOps => _,
  genericWrapArray => _,
  wrapRefArray => _,
  wrapIntArray => _,
  wrapDoubleArray => _,
  wrapLongArray => _,
  wrapFloatArray => _,
  wrapCharArray => _,
  wrapByteArray => _,
  wrapShortArray => _,
  wrapBooleanArray => _,
  wrapUnitArray => _,
  wrapString => _,
  copyArrayToImmutableIndexedSeq => _,
  _
}

object ArrayOps {

  private class ArrayView[A](xs: Array[A]) extends IndexedSeqView[A] {
    def length = xs.length
    def apply(n: Int) = xs(n)
    override def className = "ArrayView"
  }

  /** A lazy filtered array. No filtering is applied until one of `foreach`, `map` or `flatMap` is called. */
  class WithFilter[A](p: A => Boolean, xs: Array[A]) {

    private[this] implicit def elemTag: ClassTag[A] = ClassTag(xs.getClass.getComponentType)

    /** Apply `f` to each element for its side effects.
      * Note: [U] parameter needed to help scalac's type inference.
      */
    def foreach[U](f: A => U): Unit = {
      val len = xs.length
      var i = 0
      while(i < len) {
        val x = xs(i)
        if(p(x)) f(xs(i))
        i += 1
      }
    }

    /** Builds a new array by applying a function to all elements of this array.
      *
      *  @param f      the function to apply to each element.
      *  @tparam B     the element type of the returned array.
      *  @return       a new aray resulting from applying the given function
      *                `f` to each element of this array and collecting the results.
      */
    def map[B: ClassTag](f: A => B): Array[B] = {
      val b = ArrayBuilder.make[B]
      var i = 0
      while (i < xs.length) {
        val x = xs(i)
        if(p(x)) b += f(x)
        i = i + 1
      }
      b.result()
    }

    /** Builds a new array by applying a function to all elements of this array
      * and using the elements of the resulting collections.
      *
      *  @param f      the function to apply to each element.
      *  @tparam B     the element type of the returned array.
      *  @return       a new array resulting from applying the given collection-valued function
      *                `f` to each element of this array and concatenating the results.
      */
    def flatMap[B: ClassTag](f: A => IterableOnce[B]): Array[B] = {
      val b = ArrayBuilder.make[B]
      var i = 0
      while(i < xs.length) {
        val x = xs(i)
        if(p(x)) b ++= f(xs(i))
        i += 1
      }
      b.result()
    }

    def flatMap[BS, B](f: A => BS)(implicit asIterable: BS => Iterable[B], m: ClassTag[B]): Array[B] =
      flatMap[B](x => asIterable(f(x)))

    /** Creates a new non-strict filter which combines this filter with the given predicate. */
    def withFilter(q: A => Boolean): WithFilter[A] = new WithFilter[A](a => p(a) && q(a), xs)
  }

  private class ArrayIterator[A](private[this] val xs: Array[A]) extends Iterator[A] {
    private[this] var pos = 0
    def hasNext: Boolean = pos < xs.length
    def next(): A = try {
      val r = xs(pos)
      pos += 1
      r
    } catch { case _: ArrayIndexOutOfBoundsException => throw new NoSuchElementException }
  }

  private class ReverseIterator[A](private[this] val xs: Array[A]) extends Iterator[A] {
    private[this] var pos = xs.length-1
    def hasNext: Boolean = pos >= 0
    def next(): A = try {
      val r = xs(pos)
      pos -= 1
      r
    } catch { case _: ArrayIndexOutOfBoundsException => throw new NoSuchElementException }
  }

  private class GroupedIterator[A](xs: Array[A], groupSize: Int) extends Iterator[Array[A]] {
    private[this] var pos = 0
    def hasNext: Boolean = pos < xs.length
    def next(): Array[A] = {
      if(pos >= xs.length) throw new NoSuchElementException
      val r = new ArrayOps(xs).slice(pos, pos+groupSize)
      pos += groupSize
      r
    }
  }

}

/** This class serves as a wrapper for `Array`s with many of the operations found in
  *  indexed sequences. Where needed, instances of arrays are implicitly converted
  *  into this class. There is generally no reason to create an instance explicitly or use
  *  an `ArrayOps` type. It is better to work with plain `Array` types instead and rely on
  *  the implicit conversion to `ArrayOps` when calling a method (which does not actually
  *  allocate an instance of `ArrayOps` because it is a value class).
  *
  *  Neither `Array` nor `ArrayOps` are proper collection types
  *  (i.e. they do not extend `Iterable` or even `IterableOnce`). `mutable.ArraySeq` and
  *  `immutable.ArraySeq` serve this purpose.
  *
  *  The difference between this class and `ArraySeq`s is that calling transformer methods such as
 *   `filter` and `map` will yield an array, whereas an `ArraySeq` will remain an `ArraySeq`.
  *
  *  @since 2.8
  *
  *  @tparam A   type of the elements contained in this array.
  */
final class ArrayOps[A](val xs: Array[A]) extends AnyVal {

  private[this] implicit def elemTag: ClassTag[A] = ClassTag(xs.getClass.getComponentType)

  /** The size of this array.
    *
    *  @return    the number of elements in this array.
    */
  @`inline` def size: Int = xs.length

  /** The size of this array.
    *
    *  @return    the number of elements in this array.
    */
  @`inline` def knownSize: Int = xs.length

  /** Tests whether the array is empty.
    *
    *  @return    `true` if the array contains no elements, `false` otherwise.
    */
  @`inline` def isEmpty: Boolean = xs.length == 0

  /** Tests whether the array is not empty.
    *
    *  @return    `true` if the array contains at least one element, `false` otherwise.
    */
  @`inline` def nonEmpty: Boolean = xs.length != 0

  /** Selects the first element of this array.
    *
    *  @return  the first element of this array.
    *  @throws NoSuchElementException if the array is empty.
    */
  def head: A = try xs.apply(0) catch { case _: ArrayIndexOutOfBoundsException => throw new NoSuchElementException("head of empty array") }

  /** Selects the last element.
    *
    * @return The last element of this array.
    * @throws NoSuchElementException If the array is empty.
    */
  def last: A = try xs.apply(xs.length-1) catch { case _: ArrayIndexOutOfBoundsException => throw new NoSuchElementException("last of empty array") }

  /** Optionally selects the first element.
    *
    *  @return  the first element of this array if it is nonempty,
    *           `None` if it is empty.
    */
  def headOption: Option[A] = if(isEmpty) None else Some(head)

  /** Optionally selects the last element.
    *
    *  @return  the last element of this array$ if it is nonempty,
    *           `None` if it is empty.
    */
  def lastOption: Option[A] = if(isEmpty) None else Some(last)

  /** Compares the length of this array to a test value.
    *
    *   @param   len   the test value that gets compared with the length.
    *   @return  A value `x` where
    *   {{{
    *        x <  0       if this.length <  len
    *        x == 0       if this.length == len
    *        x >  0       if this.length >  len
    *   }}}
    */
  def lengthCompare(len: Int): Int = xs.length - len

  /** Selects an interval of elements. The returned array is made up
    * of all elements `x` which satisfy the invariant:
    * {{{
    *   from <= indexOf(x) < until
    * }}}
    *
    *  @param from   the lowest index to include from this array.
    *  @param until  the lowest index to EXCLUDE from this array.
    *  @return  an array containing the elements greater than or equal to
    *           index `from` extending up to (but not including) index `until`
    *           of this array.
    */
  def slice(from: Int, until: Int): Array[A] = {
    val lo = max(from, 0)
    val hi = min(until, xs.length)
    val len = hi - lo
    if(len > 0) {
      ((xs: Array[_]) match {
        case x: Array[AnyRef]     => java.util.Arrays.copyOfRange(x, lo, hi)
        case x: Array[Int]        => java.util.Arrays.copyOfRange(x, lo, hi)
        case x: Array[Double]     => java.util.Arrays.copyOfRange(x, lo, hi)
        case x: Array[Long]       => java.util.Arrays.copyOfRange(x, lo, hi)
        case x: Array[Float]      => java.util.Arrays.copyOfRange(x, lo, hi)
        case x: Array[Char]       => java.util.Arrays.copyOfRange(x, lo, hi)
        case x: Array[Byte]       => java.util.Arrays.copyOfRange(x, lo, hi)
        case x: Array[Short]      => java.util.Arrays.copyOfRange(x, lo, hi)
        case x: Array[Boolean]    => java.util.Arrays.copyOfRange(x, lo, hi)
        case x: Array[Unit]       =>
          val res = new Array[Unit](len)
          Array.copy(xs, lo, res, 0, len)
          res
      }).asInstanceOf[Array[A]]
    } else new Array[A](0)
  }

  /** The rest of the array without its first element. */
  def tail: Array[A] =
    if(xs.length == 0) throw new UnsupportedOperationException("tail of empty array") else slice(1, xs.length)

  /** The initial part of the array without its last element. */
  def init: Array[A] =
    if(xs.length == 0) throw new UnsupportedOperationException("init of empty array") else slice(0, xs.length-1)

  /** Iterates over the tails of this array. The first value will be this
    * array and the final one will be an empty array, with the intervening
    * values the results of successive applications of `tail`.
    *
    *  @return   an iterator over all the tails of this array
    */
  def tails: Iterator[Array[A]] = iterateUntilEmpty(xs => new ArrayOps(xs).tail)

  /** Iterates over the inits of this array. The first value will be this
    * array and the final one will be an empty array, with the intervening
    * values the results of successive applications of `init`.
    *
    *  @return  an iterator over all the inits of this array
    */
  def inits: Iterator[Array[A]] = iterateUntilEmpty(xs => new ArrayOps(xs).init)

  // A helper for tails and inits.
  private[this] def iterateUntilEmpty(f: Array[A] => Array[A]): Iterator[Array[A]] =
    Iterator.iterate(xs)(f).takeWhile(x => x.length != 0) ++ Iterator(Array.empty[A])

  /** An array containing the first `n` elements of this array. */
  def take(n: Int): Array[A] = slice(0, min(n, xs.length))

  /** The rest of the array without its `n` first elements. */
  def drop(n: Int): Array[A] = slice(min(n, xs.length), xs.length)

  /** An array containing the last `n` elements of this array. */
  def takeRight(n: Int): Array[A] = drop(xs.length - max(n, 0))

  /** The rest of the array without its `n` last elements. */
  def dropRight(n: Int): Array[A] = take(xs.length - max(n, 0))

  /** Takes longest prefix of elements that satisfy a predicate.
    *
    *  @param   p  The predicate used to test elements.
    *  @return  the longest prefix of this array whose elements all satisfy
    *           the predicate `p`.
    */
  def takeWhile(p: A => Boolean): Array[A] = {
    val i = indexWhere(x => !p(x))
    val hi = if(i < 0) xs.length else i
    slice(0, hi)
  }

  /** Drops longest prefix of elements that satisfy a predicate.
    *
    *  @param   p  The predicate used to test elements.
    *  @return  the longest suffix of this array whose first element
    *           does not satisfy the predicate `p`.
    */
  def dropWhile(p: A => Boolean): Array[A] = {
    val i = indexWhere(x => !p(x))
    val lo = if(i < 0) xs.length else i
    slice(lo, xs.length)
  }

  def iterator: Iterator[A] = new ArrayOps.ArrayIterator[A](xs)

  /** Partitions elements in fixed size arrays.
    *  @see [[scala.collection.Iterator]], method `grouped`
    *
    *  @param size the number of elements per group
    *  @return An iterator producing arrays of size `size`, except the
    *          last will be less than size `size` if the elements don't divide evenly.
    */
  def grouped(size: Int): Iterator[Array[A]] = new ArrayOps.GroupedIterator[A](xs, size)

  /** Splits this array into a prefix/suffix pair according to a predicate.
    *
    *  Note: `c span p`  is equivalent to (but more efficient than)
    *  `(c takeWhile p, c dropWhile p)`, provided the evaluation of the
    *  predicate `p` does not cause any side-effects.
    *
    *  @param p the test predicate
    *  @return  a pair consisting of the longest prefix of this array whose
    *           chars all satisfy `p`, and the rest of this array.
    */
  def span(p: A => Boolean): (Array[A], Array[A]) = {
    val i = indexWhere(x => !p(x))
    val idx = if(i < 0) xs.length else i
    (slice(0, idx), slice(idx, xs.length))
  }

  /** Splits this array into two at a given position.
    * Note: `c splitAt n` is equivalent to `(c take n, c drop n)`.
    *
    *  @param n the position at which to split.
    *  @return  a pair of arrayss consisting of the first `n`
    *           elements of this array, and the other elements.
    */
  def splitAt(n: Int): (Array[A], Array[A]) = (take(n), drop(n))

  /** A pair of, first, all elements that satisfy prediacte `p` and, second, all elements that do not. */
  def partition(p: A => Boolean): (Array[A], Array[A]) = {
    var res1, res2 = ArrayBuilder.make[A]
    var i = 0
    while(i < xs.length) {
      val x = xs(i)
      (if(p(x)) res1 else res2) += x
      i += 1
    }
    (res1.result(), res2.result())
  }

  /** Returns a new array with the elements in reversed order. */
  def reverse: Array[A] = {
    var len = xs.length
    var res = new Array[A](len)
    var i = 0
    while(i < len) {
      res(len-i-1) = xs(i)
      i += 1
    }
    res
  }

  /** An iterator yielding elements in reversed order.
    *
    * Note: `xs.reverseIterator` is the same as `xs.reverse.iterator` but implemented more efficiently.
    *
    *  @return  an iterator yielding the elements of this array in reversed order
    */
  def reverseIterator: Iterator[A] = new ArrayOps.ReverseIterator[A](xs)

  /** Selects all elements of this array which satisfy a predicate.
    *
    *  @param p  the predicate used to test elements.
    *  @return   a new array consisting of all elements of this array that satisfy the given predicate `p`.
    */
  def filter(p: A => Boolean): Array[A] = {
    var res = ArrayBuilder.make[A]
    var i = 0
    while(i < xs.length) {
      val x = xs(i)
      if(p(x)) res += x
      i += 1
    }
    res.result()
  }

  /** Selects all elements of this array which do not satisfy a predicate.
    *
    *  @param pred  the predicate used to test elements.
    *  @return      a new array consisting of all elements of this array that do not satisfy the given predicate `pred`.
    */
  def filterNot(p: A => Boolean): Array[A] = filter(x => !p(x))

  /** Sorts this array according to an Ordering.
    *
    *  The sort is stable. That is, elements that are equal (as determined by
    *  `lt`) appear in the same order in the sorted sequence as in the original.
    *
    *  @see [[scala.math.Ordering]]
    *
    *  @param  ord the ordering to be used to compare elements.
    *  @return     an array consisting of the elements of this array
    *              sorted according to the ordering `ord`.
    */
  def sorted[B >: A](implicit ord: Ordering[B]): Array[A] = {
    val len = xs.length
    if(xs.getClass.getComponentType.isPrimitive && len > 1) {
      // need to copy into a boxed representation to use Java's Arrays.sort
      val a = new Array[AnyRef](len)
      var i = 0
      while(i < len) {
        a(i) = xs(i).asInstanceOf[AnyRef]
        i += 1
      }
      java.util.Arrays.sort(a, ord.asInstanceOf[Ordering[AnyRef]])
      val res = new Array[A](len)
      i = 0
      while(i < len) {
        res(i) = a(i).asInstanceOf[A]
        i += 1
      }
      res
    } else {
      val copy = slice(0, len)
      if(len > 1)
        java.util.Arrays.sort(copy.asInstanceOf[Array[AnyRef]], ord.asInstanceOf[Ordering[AnyRef]])
      copy
    }
  }

  /** Sorts this array according to a comparison function.
    *
    *  The sort is stable. That is, elements that are equal (as determined by
    *  `lt`) appear in the same order in the sorted sequence as in the original.
    *
    *  @param  lt  the comparison function which tests whether
    *              its first argument precedes its second argument in
    *              the desired ordering.
    *  @return     an array consisting of the elements of this array
    *              sorted according to the comparison function `lt`.
    */
  def sortWith(lt: (A, A) => Boolean): Array[A] = sorted(Ordering.fromLessThan(lt))

  /** Sorts this array according to the Ordering which results from transforming
    *  an implicitly given Ordering with a transformation function.
    *
    *  @see [[scala.math.Ordering]]
    *  @param   f the transformation function mapping elements
    *           to some other domain `B`.
    *  @param   ord the ordering assumed on domain `B`.
    *  @tparam  B the target type of the transformation `f`, and the type where
    *           the ordering `ord` is defined.
    *  @return  an array consisting of the elements of this array
    *           sorted according to the ordering where `x < y` if
    *           `ord.lt(f(x), f(y))`.
    */
  def sortBy[B](f: A => B)(implicit ord: Ordering[B]): Array[A] = sorted(ord on f)

  /** Creates a non-strict filter of this array.
    *
    *  Note: the difference between `c filter p` and `c withFilter p` is that
    *        the former creates a new array, whereas the latter only
    *        restricts the domain of subsequent `map`, `flatMap`, `foreach`,
    *        and `withFilter` operations.
    *
    *  @param p   the predicate used to test elements.
    *  @return    an object of class `ArrayOps.WithFilter`, which supports
    *             `map`, `flatMap`, `foreach`, and `withFilter` operations.
    *             All these operations apply to those elements of this array
    *             which satisfy the predicate `p`.
    */
  def withFilter(p: A => Boolean): ArrayOps.WithFilter[A] = new ArrayOps.WithFilter[A](p, xs)

  /** Finds index of first occurrence of some value in this array after or at some start index.
    *
    *  @param   elem   the element value to search for.
    *  @tparam  B      the type of the element `elem`.
    *  @param   from   the start index
    *  @return  the index `>= from` of the first element of this array that is equal (as determined by `==`)
    *           to `elem`, or `-1`, if none exists.
    */
  def indexOf[B >: A](elem: B, from: Int = 0): Int = {
    var i = from
    while(i < xs.length) {
      if(elem == xs(i)) return i
      i += 1
    }
    -1
  }

  /** Finds index of the first element satisfying some predicate after or at some start index.
    *
    *  @param   p     the predicate used to test elements.
    *  @param   from  the start index
    *  @return  the index `>= from` of the first element of this array that satisfies the predicate `p`,
    *           or `-1`, if none exists.
    */
  def indexWhere(f: A => Boolean, from: Int = 0): Int = {
    var i = from
    while(i < xs.length) {
      if(f(xs(i))) return i
      i += 1
    }
    -1
  }

  /** Finds index of last occurrence of some value in this array before or at a given end index.
    *
    *  @param   elem   the element value to search for.
    *  @param   end    the end index.
    *  @tparam  B      the type of the element `elem`.
    *  @return  the index `<= end` of the last element of this array that is equal (as determined by `==`)
    *           to `elem`, or `-1`, if none exists.
    */
  def lastIndexOf[B >: A](elem: B, end: Int = xs.length - 1): Int = {
    var i = math.min(end, xs.length-1)
    while(i >= 0) {
      if(elem == xs(i)) return i
      i -= 1
    }
    -1
  }

  /** Finds index of last element satisfying some predicate before or at given end index.
    *
    *  @param   p     the predicate used to test elements.
    *  @return  the index `<= end` of the last element of this array that satisfies the predicate `p`,
    *           or `-1`, if none exists.
    */
  def lastIndexWhere(p: A => Boolean, end: Int = xs.length - 1): Int = {
    var i = math.min(end, xs.length-1)
    while(i >= 0) {
      if(p(xs(i))) return i
      i -= 1
    }
    -1
  }

  /** Finds the first element of the array satisfying a predicate, if any.
    *
    *  @param p       the predicate used to test elements.
    *  @return        an option value containing the first element in the array
    *                 that satisfies `p`, or `None` if none exists.
    */
  def find(f: A => Boolean): Option[A] = {
    var idx = indexWhere(f)
    if(idx == -1) None else Some(xs(idx))
  }

  /** Tests whether a predicate holds for at least one element of this array.
    *
    *  @param   p     the predicate used to test elements.
    *  @return        `true` if the given predicate `p` is satisfied by at least one element of this array, otherwise `false`
    */
  def exists(f: A => Boolean): Boolean = indexWhere(f) >= 0

  /** Tests whether a predicate holds for all elements of this array.
    *
    *  @param   p     the predicate used to test elements.
    *  @return        `true` if this array is empty or the given predicate `p`
    *                 holds for all elements of this array, otherwise `false`.
    */
  def forall(f: A => Boolean): Boolean = {
    var i = 0
    while(i < xs.length) {
      if(!f(xs(i))) return false
      i += 1
    }
    true
  }

  /** Applies a binary operator to a start value and all elements of this array,
    * going left to right.
    *
    *  @param   z    the start value.
    *  @param   op   the binary operator.
    *  @tparam  B    the result type of the binary operator.
    *  @return  the result of inserting `op` between consecutive elements of this array,
    *           going left to right with the start value `z` on the left:
    *           {{{
    *             op(...op(z, x_1), x_2, ..., x_n)
    *           }}}
    *           where `x,,1,,, ..., x,,n,,` are the elements of this array.
    *           Returns `z` if this array is empty.
    */
  def foldLeft[B](z: B)(op: (B, A) => B): B = {
    def f[@specialized(AnyRef, Int, Double, Long, Float, Char, Byte, Short, Boolean, Unit) T](xs: Array[T], op: (Any, Any) => Any, z: Any): Any = {
      val length = xs.length
      var v: Any = z
      var i = 0
      while(i < length) {
        v = op(v, xs(i))
        i += 1
      }
      v
    }
    ((xs: Any) match {
      case xs: Array[AnyRef]  => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Int]     => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Double]  => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Long]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Float]   => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Char]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Byte]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Short]   => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Boolean] => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Unit]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case null => throw new NullPointerException
    }).asInstanceOf[B]
  }

   /** Produces an array containing cumulative results of applying the binary
    *  operator going left to right.
    *
    *  @param   z    the start value.
    *  @param   op   the binary operator.
    *  @tparam  B    the result type of the binary operator.
    *  @return  array with intermediate values.
    *
    *  Example:
    *  {{{
    *    Array(1, 2, 3, 4).scanLeft(0)(_ + _) == Array(0, 1, 3, 6, 10)
    *  }}}    
    *
    */
  def scanLeft[ B : ClassTag ](z: B)(op: (B, A) => B): Array[B] = {
    var v = z
    var i = 0
    var res = new Array[B](xs.length + 1)
    while(i < xs.length) {
      res(i) = v
      v = op(v, xs(i))
      i += 1
    }
    res(i) = v
    res 
  }

  /** Computes a prefix scan of the elements of the array.
    *
    *  Note: The neutral element `z` may be applied more than once.
    *
    *  @tparam B         element type of the resulting array
    *  @param z          neutral element for the operator `op`
    *  @param op         the associative operator for the scan
    *
    *  @return           a new array containing the prefix scan of the elements in this array
    */
  def scan[B >: A : ClassTag](z: B)(op: (B, B) => B): Array[B] = scanLeft(z)(op)

   /** Produces an array containing cumulative results of applying the binary
    *  operator going right to left.
    *
    *  @param   z    the start value.
    *  @param   op   the binary operator.
    *  @tparam  B    the result type of the binary operator.
    *  @return  array with intermediate values.
    *
    *  Example:
    *  {{{
    *    Array(4, 3, 2, 1).scanRight(0)(_ + _) == Array(10, 6, 3, 1, 0)
    *  }}}    
    *
    */
  def scanRight[ B : ClassTag ](z: B)(op: (A, B) => B): Array[B] = {
    var v = z
    var i = xs.length - 1
    var res = new Array[B](xs.length + 1)
    res(xs.length) = z
    while(i >= 0) {
      v = op(xs(i), v)
      res(i) = v
      i -= 1
    }
    res 
  }

  /** Applies a binary operator to all elements of this array and a start value,
    * going right to left.
    *
    *  @param   z    the start value.
    *  @param   op   the binary operator.
    *  @tparam  B    the result type of the binary operator.
    *  @return  the result of inserting `op` between consecutive elements of this array,
    *           going right to left with the start value `z` on the right:
    *           {{{
    *             op(x_1, op(x_2, ... op(x_n, z)...))
    *           }}}
    *           where `x,,1,,, ..., x,,n,,` are the elements of this array.
    *           Returns `z` if this array is empty.
    */
  def foldRight[B](z: B)(op: (A, B) => B): B = {
    def f[@specialized(AnyRef, Int, Double, Long, Float, Char, Byte, Short, Boolean, Unit) T](xs: Array[T], op: (Any, Any) => Any, z: Any): Any = {
      var v = z
      var i = xs.length - 1
      while(i >= 0) {
        v = op(xs(i), v)
        i -= 1
      }
      v
    }
    ((xs: Any) match {
      case xs: Array[AnyRef]  => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Int]     => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Double]  => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Long]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Float]   => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Char]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Byte]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Short]   => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Boolean] => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Unit]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case null => throw new NullPointerException
    }).asInstanceOf[B]

  }

  /** Folds the elements of this array using the specified associative binary operator.
    *
    *  @tparam A1     a type parameter for the binary operator, a supertype of `A`.
    *  @param z       a neutral element for the fold operation; may be added to the result
    *                 an arbitrary number of times, and must not change the result (e.g., `Nil` for list concatenation,
    *                 0 for addition, or 1 for multiplication).
    *  @param op      a binary operator that must be associative.
    *  @return        the result of applying the fold operator `op` between all the elements, or `z` if this array is empty.
    */
  def fold[A1 >: A](z: A1)(op: (A1, A1) => A1): A1 = {
    def f[@specialized(AnyRef, Int, Double, Long, Float, Char, Byte, Short, Boolean, Unit) T](xs: Array[T], op: (Any, Any) => Any, z: Any): Any = {
      // Start with the last element and run the loop from 0 until length-1. It would be more logical to start with
      // the first element and loop from 1 until length but hotspot performs special optimizations for loops starting
      // at 0 which have a huge impact when the actual folding operation is fast.
      val length = xs.length-1
      if(length >= 0) {
        var v: Any = xs(length)
        var i = 0
        while(i < length) {
          v = op(v, xs(i))
          i += 1
        }
        v
      } else z
    }
    ((xs: Any) match {
      case xs: Array[AnyRef]  => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Int]     => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Double]  => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Long]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Float]   => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Char]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Byte]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Short]   => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Boolean] => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case xs: Array[Unit]    => f(xs, op.asInstanceOf[(Any, Any) => Any], z)
      case null => throw new NullPointerException
    }).asInstanceOf[A1]
  }

  /** Builds a new array by applying a function to all elements of this array.
    *
    *  @param f      the function to apply to each element.
    *  @tparam B     the element type of the returned array.
    *  @return       a new aray resulting from applying the given function
    *                `f` to each element of this array and collecting the results.
    */
  def map[B : ClassTag](f: A => B): Array[B] = {
    var res = new Array[B](xs.length)
    var i = 0
    while (i < xs.length) {
      res(i) = f(xs(i))
      i = i + 1
    }
    res
  }

  def mapInPlace(f: A => A): Array[A] = {
    var i = 0
    while (i < xs.length) {
      xs.update(i, f(xs(i)))
      i = i + 1
    }
    xs
  }

  /** Builds a new array by applying a function to all elements of this array
    * and using the elements of the resulting collections.
    *
    *  @param f      the function to apply to each element.
    *  @tparam B     the element type of the returned array.
    *  @return       a new array resulting from applying the given collection-valued function
    *                `f` to each element of this array and concatenating the results.
    */
  def flatMap[B: ClassTag](f: A => IterableOnce[B]): Array[B] = {
    val b = ArrayBuilder.make[B]
    var i = 0
    while(i < xs.length) {
      b ++= f(xs(i))
      i += 1
    }
    b.result()
  }

  def flatMap[BS, B](f: A => BS)(implicit asIterable: BS => Iterable[B], m: ClassTag[B]): Array[B] =
    flatMap[B](x => asIterable(f(x)))

  /** Flattens a two-dimensional array by concatenating all its rows
    *  into a single array.
    *
    *  @tparam B         Type of row elements.
    *  @param asIterable A function that converts elements of this array to rows - Iterables of type `B`.
    *  @return           An array obtained by concatenating rows of this array.
    */
  def flatten[B](implicit asIterable: A => scala.collection.Iterable[B], m: ClassTag[B]): Array[B] = {
    val b = ArrayBuilder.make[B]
    val sizes = map {
      case is: IndexedSeq[_] => is.size
      case _ => 0
    }
    b.sizeHint(new ArrayOps(sizes).fold(0)(_ + _))
    for (xs <- this)
      b ++= asIterable(xs)
    b.result()
  }

  /** Builds a new array by applying a partial function to all elements of this array
    * on which the function is defined.
    *
    *  @param pf     the partial function which filters and maps the array.
    *  @tparam B     the element type of the returned array.
    *  @return       a new array resulting from applying the given partial function
    *                `pf` to each element on which it is defined and collecting the results.
    *                The order of the elements is preserved.
    */
  def collect[B : ClassTag](f: PartialFunction[A, B]): Array[B] = {
    var i = 0
    var matched = true
    def d(x: A): B = {
      matched = false
      null.asInstanceOf[B]
    }
    val b = ArrayBuilder.make[B]
    while(i < xs.length) {
      matched = true
      val v = f.applyOrElse(xs(i), d)
      if(matched) b += v
      i += 1
    }
    b.result()
  }

  /** Finds the first element of the $coll for which the given partial function is defined, and applies the
    * partial function to it. */
  def collectFirst[B : ClassTag](f: PartialFunction[A, B]): Option[B] = {
    var i = 0
    var matched = true
    def d(x: A): B = {
      matched = false
      null.asInstanceOf[B]
    }
    while(i < xs.length) {
      matched = true
      val v = f.applyOrElse(xs(i), d)
      if(matched) return Some(v)
      i += 1
    }
    None
  }

  /** Returns an array formed from this array and another iterable collection
    * by combining corresponding elements in pairs.
    * If one of the two collections is longer than the other, its remaining elements are ignored.
    *
    *  @param   that  The iterable providing the second half of each result pair
    *  @tparam  B     the type of the second half of the returned pairs
    *  @return        a new array containing pairs consisting of corresponding elements of this array and `that`.
    *                 The length of the returned array is the minimum of the lengths of this array and `that`.
    */
  def zip[B](that: Iterable[B]): Array[(A, B)] = {
    val b = new ArrayBuilder.ofRef[(A, B)]()
    val k = that.knownSize
    b.sizeHint(if(k >= 0) min(k, xs.length) else xs.length)
    var i = 0
    val it = that.iterator
    while(i < xs.length && it.hasNext) {
      b += ((xs(i), it.next()))
      i += 1
    }
    b.result()
  }

  /** Returns an array formed from this array and another iterable collection
    *  by combining corresponding elements in pairs.
    *  If one of the two collections is shorter than the other,
    *  placeholder elements are used to extend the shorter collection to the length of the longer.
    *
    *  @param that     the iterable providing the second half of each result pair
    *  @param thisElem the element to be used to fill up the result if this array is shorter than `that`.
    *  @param thatElem the element to be used to fill up the result if `that` is shorter than this $coll.
    *  @return        a new array containing pairs consisting of corresponding elements of this array and `that`.
    *                 The length of the returned array is the maximum of the lengths of this array and `that`.
    *                 If this array is shorter than `that`, `thisElem` values are used to pad the result.
    *                 If `that` is shorter than this array, `thatElem` values are used to pad the result.
    */
  def zipAll[A1 >: A, B](that: Iterable[B], thisElem: A1, thatElem: B): Array[(A1, B)] = {
    val b = new ArrayBuilder.ofRef[(A1, B)]()
    val k = that.knownSize
    b.sizeHint(if(k >= 0) max(k, xs.length) else xs.length)
    var i = 0
    val it = that.iterator
    while(i < xs.length && it.hasNext) {
      b += ((xs(i), it.next()))
      i += 1
    }
    while(it.hasNext) {
      b += ((thisElem, it.next()))
      i += 1
    }
    while(i < xs.length) {
      b += ((xs(i), thatElem))
      i += 1
    }
    b.result()
  }

  /** Zips this array with its indices.
    *
    *  @return   A new array containing pairs consisting of all elements of this array paired with their index.
    *            Indices start at `0`.
    */
  def zipWithIndex: Array[(A, Int)] = {
    val b = new Array[(A, Int)](xs.length)
    var i = 0
    while(i < xs.length) {
      b(i) = ((xs(i), i))
      i += 1
    }
    b
  }

  /** A copy of this array with an element appended. */
  def appended[B >: A : ClassTag](x: B): Array[B] = {
    val dest = Array.copyAs[B](xs, xs.length+1)
    dest(xs.length) = x
    dest
  }

  @`inline` final def :+ [B >: A : ClassTag](x: B): Array[B] = appended(x)

  /** A copy of this array with an element prepended. */
  def prepended[B >: A : ClassTag](x: B): Array[B] = {
    val dest = new Array[B](xs.length + 1)
    dest(0) = x
    Array.copy(xs, 0, dest, 1, xs.length)
    dest
  }

  @`inline` final def +: [B >: A : ClassTag](x: B): Array[B] = prepended(x)

  /** A copy of this array with all elements of a collection prepended. */
  def prependedAll[B >: A : ClassTag](prefix: Iterable[B]): Array[B] = {
    val b = ArrayBuilder.make[B]
    val k = prefix.knownSize
    if(k >= 0) b.sizeHint(k + xs.length)
    b.addAll(prefix)
    if(k < 0) b.sizeHint(b.length + xs.length)
    b.addAll(xs)
    b.result()
  }

  /** A copy of this array with all elements of an array prepended. */
  def prependedAll[B >: A : ClassTag](prefix: Array[_ <: B]): Array[B] = {
    val dest = Array.copyAs[B](prefix, prefix.length+xs.length)
    Array.copy(xs, 0, dest, prefix.length, xs.length)
    dest
  }

  @`inline` final def ++: [B >: A : ClassTag](prefix: Iterable[B]): Array[B] = prependedAll(prefix)

  @`inline` final def ++: [B >: A : ClassTag](prefix: Array[_ <: B]): Array[B] = prependedAll(prefix)

  /** A copy of this array with all elements of a collection appended. */
  def appendedAll[B >: A : ClassTag](suffix: Iterable[B]): Array[B] = {
    val b = ArrayBuilder.make[B]
    val k = suffix.knownSize
    if(k >= 0) b.sizeHint(k + xs.length)
    b.addAll(xs)
    b.addAll(suffix)
    b.result()
  }

  /** A copy of this array with all elements of an array appended. */
  def appendedAll[B >: A : ClassTag](suffix: Array[_ <: B]): Array[B] = {
    val dest = Array.copyAs[B](xs, xs.length+suffix.length)
    Array.copy(suffix, 0, dest, xs.length, suffix.length)
    dest
  }

  @`inline` final def :++ [B >: A : ClassTag](suffix: Iterable[B]): Array[B] = appendedAll(suffix)

  @`inline` final def :++ [B >: A : ClassTag](suffix: Array[_ <: B]): Array[B] = appendedAll(suffix)

  @`inline` final def concat[B >: A : ClassTag](suffix: Iterable[B]): Array[B] = appendedAll(suffix)

  @`inline` final def concat[B >: A : ClassTag](suffix: Array[_ <: B]): Array[B] = appendedAll(suffix)

  @`inline` final def ++[B >: A : ClassTag](xs: Iterable[B]): Array[B] = appendedAll(xs)

  @`inline` final def ++[B >: A : ClassTag](xs: Array[_ <: B]): Array[B] = appendedAll(xs)

  /** Tests whether this array contains a given value as an element.
    *
    *  @param elem  the element to test.
    *  @return     `true` if this array has an element that is equal (as
    *              determined by `==`) to `elem`, `false` otherwise.
    */
  def contains[A1 >: A](elem: A1): Boolean = exists (_ == elem)

  /** Returns a copy of this array with patched values.
    * Patching at negative indices is the same as patching starting at 0.
    * Patching at indices at or larger than the length of the original array appends the patch to the end.
    * If more values are replaced than actually exist, the excess is ignored.
    *
    *  @param from       The start index from which to patch
    *  @param other      The patch values
    *  @param replaced   The number of values in the original array that are replaced by the patch.
    */
  def patch[B >: A : ClassTag](from: Int, other: IterableOnce[B], replaced: Int): Array[B] = {
    val b = ArrayBuilder.make[B]
    val k = other.knownSize
    if(k >= 0) b.sizeHint(xs.length + k - replaced)
    val chunk1 = if(from > 0) min(from, xs.length) else 0
    if(chunk1 > 0) b.addAll(xs, 0, chunk1)
    b ++= other
    val remaining = xs.length - chunk1 - replaced
    if(remaining > 0) b.addAll(xs, xs.length - remaining, remaining)
    b.result()
  }

  /** Converts an array of pairs into an array of first elements and an array of second elements.
    *
    *  @tparam A1    the type of the first half of the element pairs
    *  @tparam A2    the type of the second half of the element pairs
    *  @param asPair an implicit conversion which asserts that the element type
    *                of this Array is a pair.
    *  @param ct1    a class tag for `A1` type parameter that is required to create an instance
    *                of `Array[A1]`
    *  @param ct2    a class tag for `A2` type parameter that is required to create an instance
    *                of `Array[A2]`
    *  @return       a pair of Arrays, containing, respectively, the first and second half
    *                of each element pair of this Array.
    */
  def unzip[A1, A2](implicit asPair: A => (A1, A2), ct1: ClassTag[A1], ct2: ClassTag[A2]): (Array[A1], Array[A2]) = {
    val a1 = new Array[A1](xs.length)
    val a2 = new Array[A2](xs.length)
    var i = 0
    while (i < xs.length) {
      val e = asPair(xs(i))
      a1(i) = e._1
      a2(i) = e._2
      i += 1
    }
    (a1, a2)
  }

  /** Converts an array of triples into three arrays, one containing the elements from each position of the triple.
    *
    *  @tparam A1      the type of the first of three elements in the triple
    *  @tparam A2      the type of the second of three elements in the triple
    *  @tparam A3      the type of the third of three elements in the triple
    *  @param asTriple an implicit conversion which asserts that the element type
    *                  of this Array is a triple.
    *  @param ct1      a class tag for T1 type parameter that is required to create an instance
    *                  of Array[T1]
    *  @param ct2      a class tag for T2 type parameter that is required to create an instance
    *                  of Array[T2]
    *  @param ct3      a class tag for T3 type parameter that is required to create an instance
    *                  of Array[T3]
    *  @return         a triple of Arrays, containing, respectively, the first, second, and third
    *                  elements from each element triple of this Array.
    */
  def unzip3[A1, A2, A3](implicit asTriple: A => (A1, A2, A3), ct1: ClassTag[A1], ct2: ClassTag[A2],
                         ct3: ClassTag[A3]): (Array[A1], Array[A2], Array[A3]) = {
    val a1 = new Array[A1](xs.length)
    val a2 = new Array[A2](xs.length)
    val a3 = new Array[A3](xs.length)
    var i = 0
    while (i < xs.length) {
      val e = asTriple(xs(i))
      a1(i) = e._1
      a2(i) = e._2
      a3(i) = e._3
      i += 1
    }
    (a1, a2, a3)
  }

  /** Transposes a two dimensional array.
    *
    *  @tparam B       Type of row elements.
    *  @param asArray  A function that converts elements of this array to rows - arrays of type `B`.
    *  @return         An array obtained by replacing elements of this arrays with rows the represent.
    */
  def transpose[B](implicit asArray: A => Array[B]): Array[Array[B]] = {
    val aClass = xs.getClass.getComponentType
    val bb = new ArrayBuilder.ofRef[Array[B]]()(ClassTag[Array[B]](aClass))
    if (xs.length == 0) bb.result()
    else {
      def mkRowBuilder() = ArrayBuilder.make[B](ClassTag[B](aClass.getComponentType))
      val bs = new ArrayOps(asArray(xs(0))).map((x: B) => mkRowBuilder())
      var j = 0
      for (xs <- this) {
        var i = 0
        for (x <- new ArrayOps(asArray(xs))) {
          bs(i) += x
          i += 1
        }
      }
      for (b <- new ArrayOps(bs)) bb += b.result()
      bb.result()
    }
  }

  /** Apply `f` to each element for its side effects.
    * Note: [U] parameter needed to help scalac's type inference.
    */
  def foreach[U](f: A => U): Unit = {
    val len = xs.length
    var i = 0
    while(i < len) {
      f(xs(i))
      i += 1
    }
  }

  /** Selects all the elements of this array ignoring the duplicates.
    *
    * @return a new array consisting of all the elements of this array without duplicates.
    */
  def distinct: Array[A] = distinctBy(identity)

  /** Selects all the elements of this array ignoring the duplicates as determined by `==` after applying
    * the transforming function `f`.
    *
    * @param f The transforming function whose result is used to determine the uniqueness of each element
    * @tparam B the type of the elements after being transformed by `f`
    * @return a new array consisting of all the elements of this array without duplicates.
    */
  def distinctBy[B](f: A => B): Array[A] =
    ArrayBuilder.make[A].addAll(iterator.distinctBy(f)).result()

  /** A copy of this array with an element value appended until a given target length is reached.
    *
    *  @param   len   the target length
    *  @param   elem  the padding value
    *  @tparam B      the element type of the returned array.
    *  @return a new array consisting of
    *          all elements of this array followed by the minimal number of occurrences of `elem` so
    *          that the resulting collection has a length of at least `len`.
    */
  def padTo[B >: A : ClassTag](len: Int, elem: B): Array[B] = {
    var i = xs.length
    val newlen = max(i, len)
    val dest = Array.copyAs[B](xs, newlen)
    while(i < newlen) {
      dest(i) = elem
      i += 1
    }
    dest
  }

  /** Produces the range of all indices of this sequence.
    *
    *  @return  a `Range` value from `0` to one less than the length of this array.
    */
  def indices: Range = Range(0, xs.length)

  /** Partitions this array into a map of arrays according to some discriminator function.
    *
    *  @param f     the discriminator function.
    *  @tparam K    the type of keys returned by the discriminator function.
    *  @return      A map from keys to arrays such that the following invariant holds:
    *               {{{
    *                 (xs groupBy f)(k) = xs filter (x => f(x) == k)
    *               }}}
    *               That is, every key `k` is bound to an array of those elements `x`
    *               for which `f(x)` equals `k`.
    */
  def groupBy[K](f: A => K): immutable.Map[K, Array[A]] = {
    val m = mutable.Map.empty[K, ArrayBuilder[A]]
    val len = xs.length
    var i = 0
    while(i < len) {
      val elem = xs(i)
      val key = f(elem)
      val bldr = m.getOrElseUpdate(key, ArrayBuilder.make[A])
      bldr += elem
      i += 1
    }
    m.mapValues(_.result()).toMap
  }

  /**
    * Partitions this array into a map of arrays according to a discriminator function `key`.
    * Each element in a group is transformed into a value of type `B` using the `value` function.
    *
    * It is equivalent to `groupBy(key).mapValues(_.map(f))`, but more efficient.
    *
    * {{{
    *   case class User(name: String, age: Int)
    *
    *   def namesByAge(users: Array[User]): Map[Int, Array[String]] =
    *     users.groupMap(_.age)(_.name)
    * }}}
    *
    * @param key the discriminator function
    * @param f the element transformation function
    * @tparam K the type of keys returned by the discriminator function
    * @tparam B the type of values returned by the transformation function
    */
  def groupMap[K, B : ClassTag](key: A => K)(f: A => B): immutable.Map[K, Array[B]] = {
    val m = mutable.Map.empty[K, ArrayBuilder[B]]
    val len = xs.length
    var i = 0
    while(i < len) {
      val elem = xs(i)
      val k = key(elem)
      val bldr = m.getOrElseUpdate(k, ArrayBuilder.make[B])
      bldr += f(elem)
      i += 1
    }
    m.mapValues(_.result()).toMap
  }

  @`inline` final def toSeq: immutable.Seq[A] = toIndexedSeq

  def toIndexedSeq: immutable.IndexedSeq[A] =
    immutable.ArraySeq.unsafeWrapArray(Array.copyOf(xs, xs.length))

  /** Copy elements of this array to another array.
    *  Fills the given array `dest` starting at index `start` with at most `len` values.
    *  Copying will stop once either the all elements of this array have been copied,
    *  or the end of the array is reached, or `len` elements have been copied.
    *
    *  @param  dest   the array to fill.
    *  @param  start  the starting index.
    *  @param  len    the maximal number of elements to copy.
    *  @tparam B      the type of the elements of the array.
    */
  def copyToArray[B >: A](dest: Array[B], start: Int, len: Int = Int.MaxValue): dest.type = {
    Array.copy(xs, 0, dest, start, math.min(xs.length, math.min(dest.length-start, len)))
    dest
  }

  /** Create a copy of this array with the specified element type. */
  def toArray[B >: A: ClassTag]: Array[B] = copyToArray(new Array[B](xs.length), 0)

  /** Counts the number of elements in this array which satisfy a predicate */
  def count(p: A => Boolean): Int = {
    var i, res = 0
    val len = xs.length
    while(i < len) {
      if(p(xs(i))) res += 1
      i += 1
    }
    res
  }

  // can't use a default arg because we already have another overload with a default arg
  /** Tests whether this array starts with the given array. */
  @`inline` def startsWith[B >: A](that: Array[B]): Boolean = startsWith(that, 0)

  /** Tests whether this array contains the given array at a given index.
    *
    * @param  that    the array to test
    * @param  offset  the index where the array is searched.
    * @return `true` if the array `that` is contained in this array at
    *         index `offset`, otherwise `false`.
    */
  def startsWith[B >: A](that: Array[B], offset: Int): Boolean = {
    val thatl = that.length
    if(thatl > xs.length-offset) false
    else {
      var i = 0
      while(i < thatl) {
        if(xs(i+offset) != that(i)) return false
        i += 1
      }
      true
    }
  }

  /** Tests whether this array ends with the given array.
    *
    *  @param  that    the array to test
    *  @return `true` if this array has `that` as a suffix, `false` otherwise.
    */
  def endsWith[B >: A](that: Array[B]): Boolean = {
    val thatl = that.length
    val off = xs.length - thatl
    if(off < 0) false
    else {
      var i = 0
      while(i < thatl) {
        if(xs(i+off) != that(i)) return false
        i += 1
      }
      true
    }
  }

  /** A copy of this array with one single replaced element.
    *  @param  index  the position of the replacement
    *  @param  elem   the replacing element
    *  @return a new array which is a copy of this array with the element at position `index` replaced by `elem`.
    *  @throws IndexOutOfBoundsException if `index` does not satisfy `0 <= index < length`.
    */
  def updated[B >: A : ClassTag](index: Int, elem: B): Array[B] = {
    if(index < 0 || index >= xs.length) throw new IndexOutOfBoundsException
    val dest = toArray[B]
    dest(index) = elem
    dest
  }

  @`inline` def view: IndexedSeqView[A] = new ArrayOps.ArrayView[A](xs)


  /* ************************************************************************************************************
     The remaining methods are provided for completeness but they delegate to mutable.ArraySeq implementations which
     may not provide the best possible performance. We need them in `ArrayOps` because their return type
     mentions `C` (which is `Array[A]` in `StringOps` and `mutable.ArraySeq[A]` in `mutable.ArraySeq`).
     ************************************************************************************************************ */


  /** Computes the multiset difference between this array and another sequence.
    *
    *  @param that   the sequence of elements to remove
    *  @return       a new array which contains all elements of this array
    *                except some of occurrences of elements that also appear in `that`.
    *                If an element value `x` appears
    *                ''n'' times in `that`, then the first ''n'' occurrences of `x` will not form
    *                part of the result, but any following occurrences will.
    */
  def diff(that: Seq[_ >: A]): Array[A] = mutable.ArraySeq.make(xs).diff(that).array

  /** Computes the multiset intersection between this array and another sequence.
    *
    *  @param that   the sequence of elements to intersect with.
    *  @return       a new array which contains all elements of this array
    *                which also appear in `that`.
    *                If an element value `x` appears
    *                ''n'' times in `that`, then the first ''n'' occurrences of `x` will be retained
    *                in the result, but any following occurrences will be omitted.
    */
  def intersect(that: Seq[_ >: A]): Array[A] = mutable.ArraySeq.make(xs).intersect(that).array

  /** Groups chars in fixed size blocks by passing a "sliding window"
    *  over them (as opposed to partitioning them, as is done in grouped.)
    *  @see [[scala.collection.Iterator]], method `sliding`
    *
    *  @param size the number of chars per group
    *  @param step the distance between the first chars of successive groups
    *  @return An iterator producing strings of size `size`, except the
    *          last element (which may be the only element) will be truncated
    *          if there are fewer than `size` chars remaining to be grouped.
    */
  def sliding(size: Int, step: Int = 1): Iterator[Array[A]] = mutable.ArraySeq.make(xs).sliding(size, step).map(_.array)

  /** Iterates over combinations.  A _combination_ of length `n` is a subsequence of
    *  the original string, with the chars taken in order.  Thus, `"xy"` and `"yy"`
    *  are both length-2 combinations of `"xyy"`, but `"yx"` is not.  If there is
    *  more than one way to generate the same subsequence, only one will be returned.
    *
    *  For example, `"xyyy"` has three different ways to generate `"xy"` depending on
    *  whether the first, second, or third `"y"` is selected.  However, since all are
    *  identical, only one will be chosen.  Which of the three will be taken is an
    *  implementation detail that is not defined.
    *
    *  @return   An Iterator which traverses the possible n-element combinations of this string.
    *  @example  `"abbbc".combinations(2) = Iterator(ab, ac, bb, bc)`
    */
  def combinations(n: Int): Iterator[Array[A]] = mutable.ArraySeq.make(xs).combinations(n).map(_.array)

  /** Iterates over distinct permutations.
    *
    *  @return   An Iterator which traverses the distinct permutations of this string.
    *  @example  `"abb".permutations = Iterator(abb, bab, bba)`
    */
  def permutations: Iterator[Array[A]] = mutable.ArraySeq.make(xs).permutations.map(_.array)

  // we have another overload here, so we need to duplicate this method
  /** Tests whether this array contains the given sequence at a given index.
    *
    * @param  that    the sequence to test
    * @param  offset  the index where the sequence is searched.
    * @return `true` if the sequence `that` is contained in this array at
    *         index `offset`, otherwise `false`.
    */
  def startsWith[B >: A](that: IterableOnce[B], offset: Int = 0): Boolean = mutable.ArraySeq.make(xs).startsWith(that, offset)

  // we have another overload here, so we need to duplicate this method
  /** Tests whether this array ends with the given sequence.
    *
    *  @param  that    the sequence to test
    *  @return `true` if this array has `that` as a suffix, `false` otherwise.
    */
  def endsWith[B >: A](that: Iterable[B]): Boolean = mutable.ArraySeq.make(xs).endsWith(that)
}
