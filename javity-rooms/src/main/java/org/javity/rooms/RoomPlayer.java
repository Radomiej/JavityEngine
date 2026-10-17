package org.javity.rooms;
import org.javity.core.Component;
final class RoomPlayer extends Component {
  long lastSequence = -1;
  long lastInputNanos;
  double inputX, inputZ;
}
