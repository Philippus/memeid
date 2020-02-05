/*
 * Copyright 2019-2020 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package memeid

import java.util.{UUID => JUUID}

import _root_.scala.reflect.ClassTag
import _root_.scala.util.Try
import memeid.Bits.{readByte, toBytes, writeByte}
import memeid.Mask.{MASKS_48, MASKS_56}
import memeid.Offset.{OFFSET_48, OFFSET_56}
import memeid.digest.Digestible
import memeid.node.Node
import memeid.scala.UUID.RichUUID
import memeid.time.{Posix, Time}

package object scala {

  type UUID = memeid.UUID

  object UUID {

    val Nil: UUID = memeid.UUID.NIL
    type V1 = memeid.UUID.V1
    type V2 = memeid.UUID.V2
    type V3 = memeid.UUID.V3
    type V4 = memeid.UUID.V4
    type V5 = memeid.UUID.V5

    implicit final class RichUUID(private val uuid: memeid.UUID) extends AnyVal {

      /** The most significant 64 bits of this UUID's 128 bit value */
      @inline def msb: Long = uuid.getMostSignificantBits

      /** The least significant 64 bits of this UUID's 128 bit value */
      @inline def lsb: Long = uuid.getLeastSignificantBits

      /**
       * Returns this UUID as the provided type if versions match;
       * otherwise, returns `None`.
       *
       * @tparam A must be subtype of [[UUID]]
       * @return this [[UUID]] as the provided type if versions match;
       * otherwise, returns `None`
       */
      def as[A <: UUID: ClassTag]: Option[A] = uuid match {
        case a: A => Some(a)
        case _    => None
      }

      /**
       * Returns `true` if this UUID matches the provided type;
       * otherwise, returns `false`.
       *
       * @tparam A must be subtype of [[UUID]]
       * @return `true` if this [[UUID]] matches the provided type;
       * otherwise, returns `false`
       */
      def is[A <: UUID: ClassTag]: Boolean = uuid match {
        case _: A => true
        case _    => false
      }

    }

    def unapply(str: String): Option[UUID] =
      if (!str.isEmpty) UUID.from(str).toOption
      else None

    /**
     * Creates a valid [[UUID]] from two [[_root_.scala.Long Long]] values representing
     * the most/least significant bits.
     *
     * @param msb Most significant bit in [[_root_.scala.Long Long]] representation
     * @param lsb Least significant bit in [[_root_.scala.Long Long]] representation
     * @return a new [[UUID]] constructed from msb and lsb
     */
    def from(msb: Long, lsb: Long): UUID = memeid.UUID.from(msb, lsb)

    /**
     * Creates a valid [[UUID]] from a [[UUID]].
     *
     * @param juuid the { @link java.util.UUID}
     * @return a valid { @link UUID} created from a { @link java.util.UUID}
     */
    def fromUUID(juuid: JUUID): UUID = memeid.UUID.fromUUID(juuid)

    /**
     * Creates a [[UUID UUID]] from the [[java.util.UUID#toString string standard representation]]
     * wrapped in a [[_root_.scala.util.Right Right]].
     *
     * Returns [[_root_.scala.util.Left Left]] with the error in case the string doesn't follow the
     * string standard representation.
     *
     * @param s String for the [[java.util.UUID UUID]] to be generated as an [[UUID]]
     * @return [[_root_.scala.util.Either Either]] with [[_root_.scala.util.Left Left]] with the error in case the string doesn't follow the
     *        string standard representation or [[_root_.scala.util.Right Right]] with the [[UUID UUID]] representation.
     */
    def from(s: String): Either[Throwable, UUID] =
      Try(memeid.UUID.fromString(s)).toEither

    object V1 {

      /**
       * Construct a [[UUID.V1 V1]] (time-based) UUID.
       *
       * @param N [[node.Node Node]] for the V1 UUID generation
       * @param T [[time.Time Time]] which assures the V1 UUID time is unique
       * @return [[UUID.V1 V1]]
       */
      def next(implicit N: Node, T: Time): UUID = {
        val timestamp = T.monotonic
        val low       = readByte(Mask.TIME_LOW, Offset.TIME_LOW, timestamp)
        val mid       = readByte(Mask.TIME_MID, Offset.TIME_MID, timestamp)
        val high      = readByte(Mask.TIME_HIGH, Offset.TIME_HIGH, timestamp)
        val msb       = writeByte(Mask.VERSION, Offset.VERSION, high, 1) | (low << 32) | (mid << 16)

        val clkHigh = writeByte(
          Mask.CLOCK_SEQ_HIGH,
          Offset.CLOCK_SEQ_HIGH,
          readByte(Mask.CLOCK_SEQ_HIGH, Offset.CLOCK_SEQ_HIGH, N.id),
          0x2
        )

        val clkLow = readByte(Mask.CLOCK_SEQ_LOW, Offset.CLOCK_SEQ_LOW, N.clockSequence.toLong)

        val lsb = writeByte(
          MASKS_56,
          OFFSET_56,
          writeByte(MASKS_48, OFFSET_48, N.id, clkLow),
          clkHigh
        )

        new UUID.V1(new JUUID(msb, lsb))
      }

    }

    object V3 {

      /**
       * Construct a namespace name-based v3 UUID. Uses MD5 as a hash algorithm
       *
       * @param namespace [[UUID UUID]] used for the [[UUID.V3 V3]] generation
       * @param local     name used for the [[UUID.V3 V3]] generation
       * @param D         implicit [[digest.Digestible Digestible]] parameter
       * @tparam A Sets the type for the local and Digestible parameters
       * @return [[UUID.V3 V3]]
       */
      def apply[A](namespace: UUID, local: A)(implicit D: Digestible[A]): UUID =
        memeid.UUID.V3.from(namespace, local, D.toByteArray)

    }

    object V4 {

      /**
       * Construct a v4 (random) UUID from the given `msb` and `lsb`.
       *
       * @param msb Most significant bit in [[_root_.scala.Long Long]] representation
       * @param lsb Least significant bit in [[_root_.scala.Long Long]] representation
       * @return [[UUID.V4 V4]]
       */
      def apply(msb: Long, lsb: Long): UUID = new memeid.UUID.V4(msb, lsb)

      // Construct a v4 (random) UUID.
      def random: UUID = memeid.UUID.V4.random

      // Construct a SQUUID (random, time-based) UUID.
      def squuid(implicit P: Posix): UUID = memeid.UUID.V4.squuid(P.value)

    }

    object V5 {

      /**
       * Construct a namespace name-based v5 UUID. Uses SHA as a hash algorithm
       *
       * @param namespace [[UUID UUID]] used for the [[UUID.V5 V5]] generation
       * @param local     name used for the [[UUID.V5 V5]] generation
       * @param D         implicit [[digest.Digestible Digestible]] parameter
       * @tparam A Sets the type for the local and Digestible parameters
       * @return [[UUID.V5 V5]]
       */
      def apply[A](namespace: UUID, local: A)(implicit D: Digestible[A]): UUID =
        memeid.UUID.V5.from(namespace, local, D.toByteArray)

    }

  }

  /**
   * Implicit [[memeid.digest.Digestible Digestible]] for converting an [[UUID]] to an Array of Bytes
   */
  implicit val DigestibleUUIDInstance: Digestible[UUID] =
    u => toBytes(u.msb) ++ toBytes(u.lsb)

}